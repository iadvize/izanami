package domains.feature

import java.time.{LocalDateTime, ZoneId}
import java.time.temporal.{ChronoUnit, TemporalUnit}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import domains.Key
import domains.script.Script
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.{JsSuccess, Json}
import test.IzanamiSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class FeatureSpec extends IzanamiSpec with ScalaFutures with IntegrationPatience {

  "Feature Deserialisation" must {

    "Deserialize DefaultFeature" in {
      import Feature._
      val json = Json.parse("""
          |{
          |   "id": "id",
          |   "enabled": true,
          |   "activationStrategy": "NO_STRATEGY"
          |}
        """.stripMargin)

      val result = json.validate[Feature]
      result mustBe an[JsSuccess[_]]

      result.get must be(DefaultFeature(Key("id"), true))

    }

    "Deserialize GlobalScriptFeature" in {
      import Feature._
      val json = Json.parse("""
          |{
          |   "id": "id",
          |   "enabled": true,
          |   "activationStrategy": "GLOBAL_SCRIPT",
          |   "parameters": { "ref": "ref" }
          |}
        """.stripMargin)

      val result = json.validate[Feature]
      result mustBe an[JsSuccess[_]]

      result.get must be(GlobalScriptFeature(Key("id"), true, "ref"))

    }

    "Deserialize ScriptFeature" in {
      import Feature._
      val json = Json.parse("""
          |{
          |   "id": "id",
          |   "enabled": true,
          |   "activationStrategy": "SCRIPT",
          |   "parameters": { "script": "script" }
          |}
        """.stripMargin)

      val result = json.validate[Feature]
      result mustBe an[JsSuccess[_]]

      result.get must be(ScriptFeature(Key("id"), true, Script("script")))

    }

    "Deserialize ReleaseDateFeature" in {
      import Feature._
      val json =
        Json.parse("""
          |{
          |   "id": "id",
          |   "enabled": true,
          |   "activationStrategy": "RELEASE_DATE",
          |   "parameters": { "releaseDate": "01/01/2017 12:12:12" }
          |}
        """.stripMargin)

      val result = json.validate[Feature]
      result mustBe an[JsSuccess[_]]

      result.get must be(ReleaseDateFeature(Key("id"), true, LocalDateTime.of(2017, 1, 1, 12, 12, 12)))

    }

    "Deserialize ReleaseDateFeature other format" in {
      import Feature._
      val json =
        Json.parse("""
                     |{
                     |   "id": "id",
                     |   "enabled": true,
                     |   "activationStrategy": "RELEASE_DATE",
                     |   "parameters": { "releaseDate": "01/01/2017 12:12" }
                     |}
                   """.stripMargin)

      val result = json.validate[Feature]
      result mustBe an[JsSuccess[_]]

      result.get must be(ReleaseDateFeature(Key("id"), true, LocalDateTime.of(2017, 1, 1, 12, 12, 0)))

    }
  }

  "Feature Serialisation" should {

    "Serialize DefaultFeature" in {
      import Feature._
      val json = Json.parse("""
          |{
          |   "id": "id",
          |   "enabled": true,
          |   "activationStrategy": "NO_STRATEGY"
          |}
        """.stripMargin)

      Json.toJson(DefaultFeature(Key("id"), true)) must be(json)
    }

    "Deserialize GlobalScriptFeature" in {
      import Feature._
      val json = Json.parse("""
          |{
          |   "id": "id",
          |   "enabled": true,
          |   "activationStrategy": "GLOBAL_SCRIPT",
          |   "parameters": { "ref": "ref" }
          |}
        """.stripMargin)

      Json.toJson(GlobalScriptFeature(Key("id"), true, "ref")) must be(json)
    }

    "Deserialize ScriptFeature" in {
      import Feature._
      val json = Json.parse("""
          |{
          |   "id": "id",
          |   "enabled": true,
          |   "activationStrategy": "SCRIPT",
          |   "parameters": { "script": "script" }
          |}
        """.stripMargin)
      Json.toJson(ScriptFeature(Key("id"), true, Script("script"))) must be(json)
    }

    "Deserialize ReleaseDateFeature" in {
      import Feature._
      val json =
        Json.parse("""
          |{
          |   "id": "id",
          |   "enabled": true,
          |   "activationStrategy": "RELEASE_DATE",
          |   "parameters": { "releaseDate": "01/01/2017 12:12:12" }
          |}
        """.stripMargin)
      Json.toJson(ReleaseDateFeature(Key("id"), true, LocalDateTime.of(2017, 1, 1, 12, 12, 12))) must be(json)
    }
  }

  "Feature graph" must {
    "Serialization must be ok" in {
      val features = List(DefaultFeature(Key("a"), true),
                          DefaultFeature(Key("a:b"), false),
                          DefaultFeature(Key("a:b:c"), true),
                          DefaultFeature(Key("a:b:d"), false))

      implicit val system = ActorSystem("test")
      import system.dispatcher
      implicit val mat = ActorMaterializer()

      val graph = Source(features) via Feature.toGraph(
        Json.obj(),
        null
      ) runWith Sink.head

      graph.futureValue must be(
        Json.obj(
          "a" -> Json.obj(
            "active" -> true,
            "b" -> Json.obj(
              "active" -> false,
              "c"      -> Json.obj("active" -> true),
              "d"      -> Json.obj("active" -> false)
            )
          )
        )
      )
    }
  }


  "Date range feature" must {
    "active" in {
      val from = LocalDateTime.now(ZoneId.of("Europe/Paris")).minus(1, ChronoUnit.HOURS)
      val to = LocalDateTime.now(ZoneId.of("Europe/Paris")).plus(1, ChronoUnit.HOURS)
      val feature = DateRangeFeature(Key("key"), true, from = from, to = to)

      feature.isActive(Json.obj(), null).futureValue.getOrElse(false) must be(true)
    }

    "inactive" in {
      val from = LocalDateTime.now(ZoneId.of("Europe/Paris")).plus(1, ChronoUnit.MINUTES)
      val to = LocalDateTime.now(ZoneId.of("Europe/Paris")).plus(2, ChronoUnit.HOURS)
      val feature = DateRangeFeature(Key("key"), true, from = from, to = to)

      feature.isActive(Json.obj(), null).futureValue.getOrElse(false) must be(false)
    }
  }

  "Percentage feature" must {
    "Calc ratio" in {
      val feature = PercentageFeature(Key("key"), true, 60)

      val count: Int = calcPercentage(feature) { i =>
        s"string-number-$i"
      }

      count must (be > 55 and be < 65)

      val count2: Int = calcPercentage(feature) { i =>
        Random.nextString(50)
      }

      count2 must (be > 55 and be < 65)

    }
  }

  private def calcPercentage(feature: PercentageFeature)(mkString: Int => String) = {
    val count = (0 to 1000).map { i =>
      val isActive = feature.isActive(Json.obj("id" -> mkString(i)), null).futureValue.getOrElse(false)
      isActive
    }.count(identity) / 10
    count
  }
}
