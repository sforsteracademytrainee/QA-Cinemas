package Persistence.Domain

import Persistence.Domain.ScreenTimesOBJ.ScreenTimes
import play.api.data.Form
import play.api.data.Forms._
import slick.lifted.Tag
import slick.jdbc.MySQLProfile.api._

object BookingFormOBJ {

  case class Booking(id: Int, screenDate: String, cName: String, adults: Int, childs: Int, concession: String, screenID: Int, movieID: Int)

  val screentime = TableQuery[ScreenTimes]
  val movie = TableQuery[Movies]

  case class Bookings(tag: Tag) extends Table[Booking] (tag, "booking") {
    def id = column[Int]("FORM_ID", O.AutoInc, O.PrimaryKey)

    def screenDate = column[String]("SCREEN_DATE")
//    def screenTime = column[String]("SCREEN_TIME")
    def cName = column[String]("CUSTOMER_NAME")
    def adults = column[Int]("ADULTS")
    def childs = column[Int]("CHILDS")
    def concession = column[String]("CONCESSION")
    def screenID = column[Int]("SCREEN_ID")
    def movieID = column[Int]("MOVIE_ID")

    def screentimes = foreignKey("fk_screentime_id", screenID, screentime)(_.id, onDelete = ForeignKeyAction.Cascade)
    def movies = foreignKey("fk_movie_id_booking", movieID, movie)(_.id, onDelete = ForeignKeyAction.Cascade)

    def * = (id, screenDate, cName, adults, childs, concession, screenID, movieID) <> (Booking.tupled, Booking.unapply)

  }

  object bookingForm {
    val bookForm = Form (
      mapping (
        "id" -> default(number, 0),
        "screenDate" -> nonEmptyText,
        "cName" -> nonEmptyText,
        "adults" -> number,
        "childs" -> number,
        "concession" -> nonEmptyText,
        "screenId" -> number,
        "movieId" -> number
      )(Booking.apply)(Booking.unapply)
    )
  }

}
