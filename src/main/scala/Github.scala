package prs

import play.api.libs.ws._
import play.api.libs.ws.ahc._
import play.api.libs.json._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import fansi._
import java.io.File
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import scala.util.Try

case class Repo(owner: String, repo: String)

case class Auth(token: String)

class Http(system: ActorSystem) {

  val client = {
    implicit val sys = system
    implicit val mat = ActorMaterializer()
    AhcWSClient()
  }

  def close() = {
    client.close()
    system.shutdown()
  }
}

object Http {
  def apply() = new Http(ActorSystem("http-client"))

  implicit class RichResponse(response: Future[WSResponse]) {
    def asJson[A: Reads](implicit ec: ExecutionContext) = response.flatMap { result ⇒
      if (result.status == 200) {
        result.json.validate[A].fold({ errors ⇒
          Future.failed(
            new Exception(s"Failed to parse json response: ${JsError.toJson(errors)}")
          )
        }, Future.successful)
      } else {
        Future.failed(new Exception(s"Got invalid response status ${result.status}"))
      }
    }
  }
}

object Table {
  def apply(rows: Seq[Seq[fansi.Str]]) = {
    val colCount = rows(0).size
    val maxes = new Array[Int](colCount)
    val rowCount = rows.size

    (0 until rowCount).foreach { i =>
      (0 until colCount).foreach { j =>
        val k = rows(i)(j).length
        if (k > maxes(j)) {
          maxes(j) = k
        }
      }
    }

    rows.foreach { row =>
      row.zipWithIndex.foreach { case (col, i) =>
        val max = maxes(i)
        val size = col.length
        val padded = if (size < max) col + (" " * (max - size)) else col
        print(padded)
        print("\t")
      }
      println
    }
  }
}

import Http._

//X-RateLimit-Limit: 5000
//X-RateLimit-Remaining: 4999

case class User(login: String)

object User {
  implicit val format = Json.format[User]
}

case class Pull(html_url: String, title: String, body: String, user: User)

object Pull {
  implicit val format = Json.format[Pull]
}

class Github(client: WSClient, auth: Auth) {
  import Github._

//TODO: pagination
  def openPullRequests(repo: Repo): Future[Seq[Pull]] = {
    client.url(s"$githubHost/repos/${repo.owner}/${repo.repo}/pulls")
      .withQueryString("state" -> "open")
      .withHeaders("Authorization" -> s"token ${auth.token}")
      .get()
      .asJson[Seq[Pull]]
  }
}

object Github {
  val githubHost = "https://api.github.com"

  def apply(client: WSClient, auth: Auth): Github = new Github(client, auth)
}

class PRs(github: Github, repos: Seq[Repo]) {
  def list(): Future[Seq[(Repo, Seq[Pull])]] = Future.sequence {
    repos.map { repo =>
      github.openPullRequests(repo).map(repo -> _)
    }
  }
}

case class Settings(repos: Seq[String])

object PRs {
  def apply(client: WSClient, repos: Seq[Repo]) = new PRs(Github(client, Auth(sys.env("GITHUB_TOKEN"))), repos)

}

object Main {

  implicit class RichConfig(config: Config) {

    def to[A](implicit v: ValueReader[A]) = Try(
      config.atKey("dummy").getAs[A]("dummy")
    ).toOption.flatten
  }

  val headers = Seq(Seq("user", "title").map(Bold.On(_)))

  def printResults(results: Seq[(Repo, Seq[Pull])]) = {
    if (results.nonEmpty) {
      val data = results.filter(_._2.nonEmpty).map { case (repo, prs) =>
        val name = Color.Blue(s"${repo.owner}/${repo.repo}")
        Seq(name, Str("")) +: (if (prs.nonEmpty)  {
          headers ++ prs.map { r =>
            Seq(r.user.login, r.title).map(Str(_))
          }
        } else Seq.empty)
      }.reduce(_ ++ Seq(Seq.fill(2)(Str(""))) ++ _)
      Table(data)
    }
  }

  def configError(configFile: File) = {
    System.err.println(s"""|
      |error: Failed parsing config. Please provide a list of repos in ${configFile}, e.g.:
      |
      |  repos = [
      |    iheartradio/ficus,
      |    iheartradio/kanaloa
      |  ]""".stripMargin.trim
    )
    sys.exit(1)
  }

  def main(args: Array[String]): Unit = {
    //TODO: mergeable
    val configFile = new File(new File(sys.props("user.home"), ".prs"), "prs.conf")
    val config = ConfigFactory.parseFile(configFile)

    config.to[Settings].map { settings =>
      val http = Http()
      try {
          val repos = settings.repos.map { repo =>
            val Array(owner, name) = repo.split("/", 2)
            Repo(owner, name)
          }
          val prs = PRs(http.client, repos)
          val results = Await.result(prs.list(), 5.seconds)
          printResults(results)
        } finally {
          http.close()
        }
    }.getOrElse(configError(configFile))
  }
}


