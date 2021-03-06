package skinny.controller.feature

import org.scalatra.scalate._
import org.fusesource.scalate.TemplateEngine
import org.fusesource.scalate.layout.DefaultLayoutStrategy
import skinny._
import javax.servlet.http.HttpServletRequest
import scala.annotation.tailrec

/**
 * Scalate implementation of TemplateEngineSupport.
 *
 * This is basically same as Scalatra's Scalate support, but the convention of template file path is inspired by Ruby on Rails.
 *
 * {{{
 *   render("/members/index")
 * }}}
 *
 * The above code expects "src/main/webapp/WEB-INF/views/members/index.html.ssp" by default.
 *
 * If you need to use scaml instead,
 *
 * {{{
 *   override lazy val scalateExtension: String = "scaml"
 * }}}
 *
 * And then, Skinny expects "src/main/webapp/WEB-INF/views/members/index.html.scaml"
 */
trait ScalateTemplateEngineFeature extends TemplateEngineFeature
    with ScalateSupport
    with ScalateUrlGeneratorSupport {

  /**
   * To deal with exceptions.
   *
   * http://www.scalatra.org/guides/views/scalate.html#toc_303
   */
  override def isScalateErrorPageEnabled = false

  /**
   * Overrides to make the template path simpler.
   *
   * @return paths
   */
  override protected def defaultTemplatePath: List[String] = List("/WEB-INF/views")

  /**
   * Creates TemplateEngine instance for Skinny app.
   *
   * @param config configuration
   * @return TemplateEngine instance
   */
  override protected def createTemplateEngine(config: ConfigT) = {
    val engine = super.createTemplateEngine(config)
    engine.layoutStrategy = new DefaultLayoutStrategy(engine, TemplateEngine.templateTypes.map("/WEB-INF/layouts/default." + _): _*)
    engine.packagePrefix = "templates"
    engine
  }

  /**
   * Scalate extension names. Skinny will search for templates in this order.
   *
   * If you'd like to change the search order, or you want to
   * restrict the search to fewer template languages, override this attribute.
   *
   * Note that removing unnecessary items from this list will improve the
   * performance of the template engine.
   */
  def scalateExtensions: List[String] = List("ssp", "jade", "scaml", "mustache")

  /**
   * The Scalate template type that is searched for first.
   */
  def preferredScalateExtension = scalateExtensions.head

  /**
   * NOTICE: just for backward compatibility with version 0.9.27 or older.
   */
  @deprecated(message = "You don't need override this method anymore. This API will be removed in version 1.0.0.", since = "0.9.28")
  def scalateExtension: String = preferredScalateExtension

  /**
   * Returns the actual template path for the name.
   *
   * @param path path name
   * @param format format (HTML,JSON,XML...)
   * @return actual path
   */
  override protected def templatePaths(path: String)(implicit format: Format = Format.HTML): List[String] = {
    scalateExtensions.map(toTemplatePath(path, format, _))
  }

  /**
   * Predicates the template path is available.
   *
   * @param path path name
   * @param format format (HTML,JSON,XML...)
   * @return true/false
   */
  override protected def templateExists(path: String)(implicit format: Format = Format.HTML): Boolean = {
    val exists = findFirstAvailableTemplate(templatePaths(path)).isDefined
    if ((SkinnyEnv.isDevelopment() || SkinnyEnv.isTest()) && !exists && format == Format.HTML) {
      generateWelcomePageIfAbsent(path)
      true
    } else {
      exists
    }
  }

  @tailrec
  private def findFirstAvailableTemplate(templatePaths: List[String]): Option[String] = templatePaths match {
    case Nil => None
    case p :: ps => {
      val t = findTemplate(p)
      if (t.isDefined) t else findFirstAvailableTemplate(ps)
    }
  }

  /**
   * Generates a sample page for absent page.
   */
  protected def generateWelcomePageIfAbsent(path: String)(implicit format: Format = Format.HTML): Unit = {
    import org.apache.commons.io.FileUtils
    import java.io.File
    val filePath = servletContext.getRealPath(s"/WEB-INF/views/${toTemplatePath(path, format, preferredScalateExtension)}")
    val file = new File(filePath)
    val code: String = preferredScalateExtension match {
      case "jade" =>
        """h3 Welcome
          |hr
          |p(class="alert alert-success")
          |  b TODO:
          |  | This is an auto-generated file by
          |  a(href="http://skinny-framework.org/") Skinny framework
          |  |!
          |""".stripMargin
      case "scaml" =>
        """%h3 Welcome
          |%hr
          |p(class="alert alert-success")
          |  %b TODO:
          |  This is an auto-generated file by
          |  %a(href="http://skinny-framework.org/") Skinny framework
          |  !
          |""".stripMargin
      case _ =>
        """<h3>Welcome</h3>
          |<hr/>
          |<p class="alert alert-success">
          |  <strong>TODO:</strong> This is an auto-generated file by <a href="http://skinny-framework.org/">Skinny framework</a>!<br/>
          |</p>
          |""".stripMargin
    }
    FileUtils.write(file, code)
  }

  /**
   * Renders body with template.
   *
   * @param path path name
   * @param format format (HTML,JSON,XML...)
   * @return true/false
   */
  override protected def renderWithTemplate(path: String)(implicit format: Format = Format.HTML): String = {
    // Note: templateExists() should already have been called, so we know we have an actual template
    val templatePath = findFirstAvailableTemplate(templatePaths(path)).getOrElse("missing-template")
    layoutTemplate(templatePath, requestScope.toMap.toSeq: _*)
  }

  /**
   * Replaces layout template for this action.
   *
   * @param path the layout template path, including extension, e.g. "custom.jade"
   */
  def layout(path: String)(implicit request: HttpServletRequest): ScalateTemplateEngineFeature = {
    if (request != null) {
      val _path = path.replaceFirst("^/", "").replaceAll("//", "/").replaceFirst("/$", "")
      templateAttributes += ("layout" -> s"/WEB-INF/layouts/${_path}")
      this
    } else {
      // TODO this method doesn't work in beforeAction, more investigation
      logger.warn("You cannot specify layout here. Put this into action methods.")
      this
    }
  }

  protected def toTemplatePath(path: String, format: Format, ext: String): String = {
    s"${path}.${format.name}.${ext}".replaceAll("//", "/").replaceFirst("^/", "")
  }

}
