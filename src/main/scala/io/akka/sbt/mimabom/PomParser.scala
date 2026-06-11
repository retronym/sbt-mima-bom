package io.akka.sbt.mimabom

import java.io.File

import org.w3c.dom.{ Element, Node }

final case class BomEntry(
    organization: String,
    name: String,
    version: String,
    classifier: Option[String],
    tpe: String,
    scope: Option[String]) {
  def key: String = s"$organization:$name"
  def id: (String, String, Option[String]) = (organization, name, classifier)
  override def toString: String =
    s"$organization:$name:$version" + classifier.fold("")(c => s" (classifier $c)")
}

object PomParser {

  /**
   * Extracts all `<dependency>` entries (from `<dependencyManagement>` and `<dependencies>`) of a pom,
   * interpolating `<properties>` and the built-in `project.*` properties. Parent pom inheritance is not
   * supported: a BOM used with this plugin must be self-contained.
   */
  def parse(file: File): Seq[BomEntry] = {
    val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
    dbf.setNamespaceAware(false)
    val doc = dbf.newDocumentBuilder().parse(file)
    val project = doc.getDocumentElement

    def directChild(el: Element, tag: String): Option[Element] = {
      val children = el.getChildNodes
      (0 until children.getLength).iterator.map(children.item).collectFirst {
        case n: Element if n.getNodeType == Node.ELEMENT_NODE && n.getTagName == tag => n
      }
    }
    def directChildText(el: Element, tag: String): Option[String] =
      directChild(el, tag).map(_.getTextContent.trim)

    val parent = directChild(project, "parent")
    def projectCoord(tag: String): Option[String] =
      directChildText(project, tag).orElse(parent.flatMap(directChildText(_, tag)))

    val properties: Map[String, String] = {
      val fromSection = directChild(project, "properties").toSeq.flatMap { props =>
        val children = props.getChildNodes
        (0 until children.getLength).iterator.map(children.item).collect {
          case n: Element if n.getNodeType == Node.ELEMENT_NODE => n.getTagName -> n.getTextContent.trim
        }
      }
      val builtIns = Seq("groupId", "artifactId", "version").flatMap { tag =>
        projectCoord(tag).map(v => s"project.$tag" -> v)
      }
      (fromSection ++ builtIns).toMap
    }

    val PropertyRef = """\$\{([^}]+)\}""".r
    def interpolate(value: String, depth: Int = 0): String =
      if (!value.contains("${")) value
      else if (depth > 10) sys.error(s"Recursive property reference in $file: '$value'")
      else
        interpolate(
          PropertyRef.replaceAllIn(
            value,
            m =>
              java.util.regex.Matcher.quoteReplacement(properties.getOrElse(
                m.group(1),
                sys.error(s"Undefined property '${m.group(1)}' in $file. " +
                  "Parent pom inheritance is not supported; the BOM must be self-contained.")))),
          depth + 1)

    val nodes = doc.getElementsByTagName("dependency")
    (0 until nodes.getLength).flatMap { i =>
      val el = nodes.item(i).asInstanceOf[Element]
      // only direct children, so <exclusions> entries are not picked up
      def child(tag: String): Option[String] = directChildText(el, tag).map(interpolate(_))
      for {
        g <- child("groupId")
        a <- child("artifactId")
        v <- child("version")
      } yield BomEntry(g, a, v, child("classifier").filter(_.nonEmpty), child("type").getOrElse("jar"), child("scope"))
    }
  }
}
