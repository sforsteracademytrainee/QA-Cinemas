package controllers

import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.Results.{BadRequest, Redirect}
import Persistence.DAO.{BookingDAO, DiscussionBoardDAO, MovieDAO, PaymentDAO, ScreenTimeDAO}
import Persistence.Domain.paymentObj.{Payment, PaymentForm}
import Persistence.Domain.BookingFormOBJ.{Booking, bookingForm}
import Persistence.Domain.DiscussionBoardOBJ.{DiscussionBoard, boardForm}
import Persistence.Domain.{Movie, SearchOBJ}
import Persistence.DAO.{MovieDAO, ScreenTimeDAO}
import Persistence.Domain.{EmailOBJ, Movie}
import Persistence.Domain.ScreenTimesOBJ.ScreenTime
import play.api.libs.Codecs.sha1

import scala.concurrent.ExecutionContext.Implicits.global
import javax.inject._
import play.api.mvc._
import play.mvc.Action

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.{Failure, Success}
/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) with I18nSupport {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    Ok(views.html.index("Welcome to QA Cinemas!"))
  }

  def listingsGallery = Action.async { implicit request =>
    MovieDAO.readAll() map(movies => Ok(views.html.listingsgallery("Listings gallery", movies.filter(_.released == true))))
  }

  def newReleases = Action.async { implicit request =>
    MovieDAO.readAll() map(movies => Ok(views.html.listingsgallery("Upcoming releases", movies.filter(_.released == false))))
  }

  def readID(id: Int) = Action.async(implicit request =>
    // Used nested? futures instead of using a join
    ScreenTimeDAO.readByMID(id).flatMap { times =>
      MovieDAO.readById(id).map {
        case Some(movie) => Ok(views.html.movie(movie, times))
        case None => Ok(views.html.error("Error 404", "Could not find the movie."))
      }
    }
  )

  def deleteDiscBoard(id: Int) = Action { implicit request =>
    DiscussionBoardDAO.delete(id).onComplete{
      case Success(1) =>
        println("Discussion board entry has been deleted!")
      case Success(0) =>
        println("Something went wrong and the entry was not deleted")
      case Failure(error) =>
        error.printStackTrace()
    }
    Redirect("/adminboard")
  }

  def discBoardRead() = Action.async {implicit request => DiscussionBoardDAO.readAll() map (working => Ok(views.html.AdminDiscBoard(working)))}

  def createDiscBoard() = Action.async {implicit request =>
    DiscussionBoardDAO.readAll() map { discussions =>
      boardForm.submitForm.bindFromRequest().fold({ formsWithError =>
          BadRequest(views.html.discboard(formsWithError, discussions))
      }, {
        creator => createFunc(creator)
          Redirect("/discboard")
      }
    )}
  }

  def createFunc(discBoard: DiscussionBoard): Unit = {
    DiscussionBoardDAO.create(discBoard).onComplete {
      case Success(value) =>
        print(value)
      case Failure(exception) =>
        exception.printStackTrace()
    }
  }

  def homepage = Action {
    Ok(views.html.homepage("Welcome to QA Cinemas!"))
  }

  def aboutUs = Action {
    Ok(views.html.aboutUs())
  }

  def contactUs = Action { implicit request =>
    EmailOBJ.emailContactForm.submitForm.bindFromRequest().fold({ formWithErrors =>
       BadRequest(views.html.contactUs(formWithErrors))
    }, { widget =>
      // could send to an error page on failure to send
      EmailOBJ.emailing(widget)
      Ok(views.html.emailconfirmation())
    })
  }

  def screens = Action {
    Ok(views.html.screens())
  }

  def gettingThere = Action {
    Ok(views.html.gettingThere())
  }

  def openingTimes = Action {
    Ok(views.html.openingTimes())
  }
  
  def createPayment() = Action.async { implicit request =>
    BookingDAO.getLastIndex() flatMap { booking =>
      if (booking.isDefined) {
        MovieDAO.totalPrice(booking.get) map { price =>
          PaymentForm.submitForm.bindFromRequest().fold({ formWithErrors =>
            BadRequest(views.html.payment(PaymentForm.submitForm.fill(Payment(0,"", "", "", "", booking.get.id)), price))
          }, { widget =>
            println("form complete")
            createP(Payment(0, widget.cardHolderName, sha1(widget.cardNo), sha1(widget.expiryDate), sha1(widget.securityCode), widget.bookingID))
            Redirect("/bookingcomplete/" + booking.get.id)
          })
        }
      } else Future {NotFound(views.html.error("Error 404", "Booking not found."))}
    }
  }

  def createP(pay: Payment): Unit = {
    PaymentDAO.create(pay).onComplete {
      case Success(value) => println(value)
      case Failure(exception) => exception.printStackTrace()
    }
  }

  def createBooking(id:Int) = Action.async { implicit request =>
    MovieDAO.readById(id).flatMap { movie =>
      if(movie.isDefined) {
        ScreenTimeDAO.readByMID(id) map { screentimes =>
          bookingForm.bookForm.bindFromRequest().fold({ bookingFormWithErrors =>
            BadRequest(views.html.booking(bookingForm.bookForm.fill(Booking(0, "", "", 1, 0, "", 1, movie.get.id)), id, movie.get, screentimes))
          }, { widget =>
            createB(widget)
            Redirect("/payment")
          })
        }
      }else Future {NotFound(views.html.error("Error 404", "Booking not found."))}
    }
  }

  def createB(book: Booking): Unit = {
    BookingDAO.create(book).onComplete {
      case Success(value) =>
        println(value)
      case Failure(exception) =>
        exception.printStackTrace()
    }
  }

  def bookingComplete(id: Int) = Action.async { implicit request =>
    BookingDAO.readById(id).map {
      case Some(thing) => Ok(views.html.bookingcomplete(thing))
      case None => NotFound(views.html.error("Error 404", "Could not find the booking."))
    }
  }

  def search = Action.async { implicit request =>
    SearchOBJ.searchForm.bindFromRequest.fold(
      formWithErrors => Future { Ok(views.html.searchresults(Seq[Movie]())) },
      search => MovieDAO.search(search.term) map { results =>
        Ok(views.html.searchresults(results))
      }
    )

  }


  def Classification = Action{
    Ok(views.html.Classifications())
  }

  def Venues= Action{
    Ok(views.html.placestogo())
  }

  def tempToDo = TODO

  def bookings() = Action.async{ implicit request =>
    MovieDAO.readAll() map(movies => Ok(views.html.bookings(movies.filter(_.released == true))))
  }

}

