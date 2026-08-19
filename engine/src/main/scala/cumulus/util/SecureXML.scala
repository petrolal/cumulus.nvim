package cumulus.util

import scala.xml.factory.XMLLoader
import scala.xml.Elem
import javax.xml.parsers.{SAXParser, SAXParserFactory}
import javax.xml.XMLConstants

/**
 * A secure XML loader that prevents XML External Entity (XXE) attacks.
 * It disables DOCTYPE declarations, external entities, and XInclude.
 */
private[cumulus] object SecureXML extends XMLLoader[Elem]:
  private val factory = SAXParserFactory.newInstance()
  
  try
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setXIncludeAware(false)
    factory.setNamespaceAware(false)
  catch
    case _: Exception => // Ignore if unsupported

  private def trySetFeature(name: String, value: Boolean): Unit =
    try factory.setFeature(name, value)
    catch case _: Exception => ()

  trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
  trySetFeature("http://xml.org/sax/features/external-general-entities", false)
  trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
  trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

  override def parser: SAXParser = factory.newSAXParser()
