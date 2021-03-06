package com.sksamuel.elastic4s.mappings

import com.sksamuel.elastic4s.mappings.FieldType.{DoubleType, FloatType}
import com.sksamuel.elastic4s.testkit.ElasticSugar
import org.scalatest.{Matchers, WordSpec}

class DynamicTemplateDefinitionTest extends WordSpec with Matchers with ElasticSugar {

  "DynamicTemplateDefinition" should {
    "support mappings for wildcards" in {

      val priceTemplate = dynamicTemplate("prices").mapping(dynamicTemplateMapping(FloatType)).matching("*_price")
      val indexName = "dynamic_template_definition1"
      val typeName = "mytype"

      client.execute {
        createIndex(indexName).mappings {
          mapping(typeName) dynamicTemplates priceTemplate
        }
      }.await

      client.execute {
        indexInto(indexName / typeName).fields("my_price" -> 21.3)
      }.await

      // the my_price field should match *_price and thus create a new field with the type Float
      client.execute {
        getMapping(indexName / typeName)
      }.await.mappings(indexName)(typeName).source.toString shouldBe """{"mytype":{"dynamic_templates":[{"prices":{"match":"*_price","mapping":{"type":"float"}}}],"properties":{"my_price":{"type":"float"}}}}"""
    }
    "support unmatch" in {

      val template = DynamicTemplateDefinition("price", field typed DoubleType).matching("*_price").unmatch("my_price")

      val indexName = "dynamic_template_definition2"
      client.execute {
        createIndex(indexName).mappings {
          mapping(indexName) dynamicTemplates template
        }
      }.await

      client.execute {
        indexInto(indexName / "v") fields "my_price" -> "22"
      }.await

      val resp = client.execute {
        getMapping(indexName / "v")
      }.await

      // my_price has been explicitly unmatched and so should be ignored and left as text
      resp.mappings(indexName)("v").source.toString shouldBe """{"v":{"properties":{"my_price":{"type":"text","fields":{"keyword":{"type":"keyword","ignore_above":256}}}}}}"""
    }
  }
}
