package com.github.stijndehaes.playprometheusfilters.filters

import io.prometheus.client.CollectorRegistry
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.typedmap.TypedMap
import play.api.mvc.{Action, Results}
import play.api.routing.{HandlerDef, Router}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}

class StatusAndRouteLatencyFilterSpec extends WordSpec with MustMatchers with MockitoSugar with Results with DefaultAwaitTimeout with FutureAwaits with GuiceOneAppPerSuite  {

  private implicit val mat = app.materializer

  "Filter constructor" should {
    "Add a histogram to the prometheus registry" in {
      val collectorRegistry = mock[CollectorRegistry]
      new StatusAndRouteLatencyFilter(collectorRegistry)
      verify(collectorRegistry).register(any())
    }
  }

  "Apply method" should {
    "Measure the latency" in {
      val filter = new StatusAndRouteLatencyFilter(mock[CollectorRegistry])
      val rh = FakeRequest().withAttrs( TypedMap(
        Router.Attrs.HandlerDef -> HandlerDef(null, null, null, "test", null, null ,null ,null ,null)
      ))
      val action = Action(Ok("success"))

      await(filter(action)(rh).run())

      val metrics = filter.requestLatency.collect()
      metrics must have size 1
      val samples = metrics.get(0).samples
      //this is the count sample
      val countSample = samples.get(samples.size() - 2)
      countSample.value mustBe 1.0
      countSample.labelValues must have size 2
      countSample.labelValues.get(0) mustBe "test"
      countSample.labelValues.get(1) mustBe "200"
    }

    "Measure the latency for an unmatched route" in {
      val filter = new StatusAndRouteLatencyFilter(mock[CollectorRegistry])
      val rh = FakeRequest()
      val action = Action(NotFound("error"))

      await(filter(action)(rh).run())

      val metrics = filter.requestLatency.collect()
      metrics must have size 1
      val samples = metrics.get(0).samples
      //this is the count sample
      val countSample = samples.get(samples.size() - 2)
      countSample.value mustBe 1.0
      countSample.labelValues must have size 2
      countSample.labelValues.get(0) mustBe StatusAndRouteLatencyFilter.unmatchedRoute
      countSample.labelValues.get(1) mustBe "404"
    }
  }

}
