package controllers.admin

import java.io.{StringWriter, File, FileInputStream, InputStreamReader}

import au.com.bytecode.opencsv.{CSVWriter, CSVReader}
import auth.{HashedPassword, AccountManager}
import backend.{EventHandler, Backend}
import com.google.inject._
import controllers.base.AdminController
import controllers.core.auth.AccountHelpers
import defines.{PermissionType, ContentTypes, EntityType}
import models.{Group, UserProfileF, UserProfile, Account}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
 * Single use controller for importing legacy (Drupal6) user accounts.
 *
 * All this code should be deleted after we're done with that.
 */
@Singleton
case class LegacyAccounts @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup) extends AdminController with AccountHelpers {

  private def noEventsBackend: Backend = backend.withEventHandler(new EventHandler {
    def handleCreate(id: String) = ()
    def handleUpdate(id: String) = ()
    def handleDelete(id: String) = ()
  })

  import play.api.data.Form
  import play.api.data.Forms._

  // Form allowing selection of user ID offset.
  private val legacyUserForm: Form[Int] = Form(single("offset" -> number(min=1)))

  def importLegacyForm = AdminAction { implicit  request =>
    Ok(views.html.admin.importLegacy(legacyUserForm,
      controllers.admin.routes.LegacyAccounts.importLegacy()))
  }

  private case class LegacyData(
    uid: Int,
    userName: String,
    email: String,
    passwordMd5: String,
    picture: Option[String],
    created: DateTime,
    lastLogin: DateTime,
    title: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    country: Option[String],
    institution: Option[String],
    role: Option[String],
    interests: Option[String],
    about: Option[String],
    url: Option[String],
    workUrl: Option[String]
) {
    def name = (for {
      fn <- firstName
      ln <- lastName
    } yield s"$fn $ln")
      .orElse(firstName)
      .orElse(lastName)
      .getOrElse(userName)

  }

  private case class ParseError(line: Int, msg: String) extends Throwable(msg)

  private object LegacyData {
    def fromRow(row: Int, data: List[String]): LegacyData = {
      if (data.size < 22) throw ParseError(row, "No enough fields")
      else LegacyData(
        uid = data(0).toInt,
        userName = data(1).trim,
        email = data(3).trim.toLowerCase,
        passwordMd5 = data(2).trim,
        picture = cleanString(data(5)),
        created = new DateTime(data(6).toLong),
        lastLogin = new DateTime(data(8).toLong),
        title = cleanString(data(9)),
        firstName = cleanString(data(10)),
        lastName = cleanString(data(11)),
        country = cleanString(data(12)),
        institution = cleanString(data(13)),
        role = cleanString(data(14)),
        about = cleanString(data(17)),
        url = cleanString(data(18)),
        workUrl = cleanString(data(19)),
        interests = cleanString(data(21))
      )
    }
  }

  def importLegacy = AdminAction.async(parse.multipartFormData) { implicit request =>
    val boundForm: Form[Int] = legacyUserForm.bindFromRequest
    boundForm.fold(
      errForm => immediate(BadRequest(views.html.admin.importLegacy(errForm,
        controllers.admin.routes.LegacyAccounts.importLegacy()))),
      offset =>
        request.body.file("csv").map { file =>

          importCsv(offset, file.ref.file).map { (done: List[(Int, Account, UserProfile, Boolean)]) =>
            val msgUpdated = done.filter(_._4).map { case (uid, account, profile, _) =>
              f"${account.id}%-16s${account.email}%-40s${profile.model.name}%s"
            }.mkString("\n")
            val msgCreated = done.filterNot(_._4).map { case (uid, account, profile, _) =>
              f"${account.id}%-16s${account.email}%-40s${profile.model.name}%s"
            }.mkString("\n")

            val csv = done.map { case (uid, account, profile, updated) =>
              Array(uid.toString, account.id, account.email, profile.model.name, updated.toString)
            }
            Ok(writeCsv(Seq("UID", "ID", "EMAIL", "NAME", "UPDATED"), csv))
          } recover {
            case ParseError(row, msg) => BadRequest(s"Error at row $row: $msg")
          }

        }.getOrElse {
          immediate(BadRequest(views.html.admin.importLegacy(
            boundForm.withError("csv", "No CSV file found"),
            controllers.admin.routes.LegacyAccounts.importLegacy())))
        }
    )
  }

  private def writeCsv(headers: Seq[String], data: Seq[Array[String]]): String = {
    val buffer = new StringWriter()
    val csvWriter = new CSVWriter(buffer)
    csvWriter.writeNext(headers.toArray)
    for (item <- data) {
      csvWriter.writeNext(item)
    }
    csvWriter.close()
    buffer.getBuffer.toString
  }

  private def getAccountAndProfile(num: Int, data: LegacyData)(implicit userOpt: Option[UserProfile]): Future[(Int, Account, UserProfile, Boolean)] = {
    accounts.findByEmail(data.email).flatMap {
      case Some(existing) =>
        Logger.info("User already exists! " + data.name)
        for {
          updated <- updateProfile(existing.id, data)
        } yield (data.uid, existing, updated, true)
      case None =>
        val id = f"user$num%06d"
        for {
          userProfile <- getProfile(id, data)
          account <- getAccount(id, data)
        } yield {
          Logger.info(s"Created: ${data.email} -> ${userProfile.model}")
          (data.uid, account, userProfile, false)
        }
    }
  }

  private def cleanString(s: String): Option[String] =
    if (s == null || s.trim.isEmpty || s.trim.equalsIgnoreCase("NULL")) None else Some(s)

  private def importCsv(offset: Int, file: File)(implicit userOpt: Option[UserProfile]): Future[List[(Int, Account, UserProfile, Boolean)]] = {
    val csvReader: CSVReader = new CSVReader(
      new InputStreamReader(
        new FileInputStream(file), "UTF-8"), ',', '"', 1)

    import scala.collection.JavaConverters._
    val all = csvReader.readAll()
    val allData: List[List[String]] = (for {
      arr <- all.asScala
    } yield arr.toList).toList

    Future.sequence(allData.zipWithIndex.map { case (row, i) =>
      getAccountAndProfile(offset + i, LegacyData.fromRow(i, row))
    })
  }

  private def updateProfile(id: String, data: LegacyData)(implicit userOpt: Option[UserProfile]): Future[UserProfile] = {
    def filterBlank(s: Option[String], n: Option[String]): Option[String] =
      s.filter(_.trim.nonEmpty).orElse(n)

    noEventsBackend.get[UserProfile](id).flatMap { current =>
      val updated: UserProfileF = current.model.copy(
        about = filterBlank(current.model.about, data.about),
        url = filterBlank(current.model.url, data.url),
        workUrl = filterBlank(current.model.workUrl, data.workUrl),
        title = filterBlank(current.model.title, data.title),
        interests = filterBlank(current.model.interests, data.interests),
        role = filterBlank(current.model.role, data.role),
        location = filterBlank(current.model.location, data.country),
        institution = filterBlank(current.model.institution, data.institution)
      )
      noEventsBackend.update[UserProfile,UserProfileF](id, updated, logMsg = Some("Merging EHRI hub account data"))
    }
  }

  private def getAccount(id: String, data: LegacyData)(implicit userOpt: Option[UserProfile]): Future[Account] = {
    accounts.create(Account(
      id = id,
      email = data.email,
      staff = false,
      active = true,
      allowMessaging = true,
      password = Some(HashedPassword.fromPlain(data.passwordMd5)),
      verified = true,
      created = Some(data.created),
      lastLogin = Some(data.lastLogin),
      isLegacy = true
    ))
  }

  private def getProfile(id: String, data: LegacyData)(implicit userOpt: Option[UserProfile]): Future[UserProfile] = {
    val profile: UserProfileF = UserProfileF(
      id = Some(id),
      isA = EntityType.UserProfile,
      identifier = id,
      name = data.name,
      location = data.country,
      about = data.about,
      imageUrl = data.picture.map(_ =>
        // NB: Hard coded AWS params - did I mention this was single-use?
        // Actual image to be uploaded separately...
        s"http://ehri-users.s3-eu-west-1.amazonaws.com/images/portal.ehri-project.eu/$id.png"),
      firstNames = data.firstName,
      lastName = data.lastName,
      title = data.title,
      institution = data.institution,
      role = data.role,
      interests = data.interests,
      url = data.url,
      workUrl = data.workUrl,
      active = true
    )

    import play.api.Play.current
    for {
      profile <- noEventsBackend.create[UserProfile,UserProfileF](profile, logMsg = Some("Migrating account from EHRI hub"))
      _ <- Future.sequence(defaultPortalGroups.map(groupId => noEventsBackend.addGroup[Group, UserProfile](groupId, id)))
      _ <- noEventsBackend.setItemPermissions(id, ContentTypes.UserProfile, id, Seq(PermissionType.Owner.toString))
    } yield profile
  }
}