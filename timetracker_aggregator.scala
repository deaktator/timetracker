#!/bin/sh
exec scala -unchecked "$0" "$@"
!#

import scala.io.Source
import java.text.SimpleDateFormat
import java.util.Date
import PowerState.PowerState

/**
 * Power states range from 0 - 4, inclusive. I believe these are related to ACPI
 * global states, with the states numbers inverted.
 * https://en.wikipedia.org/wiki/Advanced_Configuration_and_Power_Interface#Global_states
 * 
 *  0: (S4 ACPI) Hibernation or Suspend to Disk.
 *  1: (S3 ACPI) Commonly referred to as Standby, Sleep, or Suspend to RAM.
 *  2: (S2 ACPI) CPU powered off.
 *  3: (S1 ACPI) Power on Suspend. Power to CPU(s) and RAM is maintained.
 *  4: (S0 ACPI) Working.
 */
object PowerState extends Enumeration {
  type PowerState = Value
  val Hibernate, Standby, CpuOff, PowerOnSuspend, Working = Value
}

case class Line(date: Date, powerState: PowerState, millisSinceTickle: Option[Int])

object Line {
  def apply(s: String): Line = {
    s.split("\t") match {
      case Array(date, powerState, millisSinceTickle) => 
        new Line(dateFormat.parse(date), PowerState(powerState.toInt), Option(millisSinceTickle.toInt))
      case Array(date, powerState) => 
        new Line(dateFormat.parse(date), PowerState(powerState.toInt), None)
    }
  }
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
}

sealed trait Interval
case class ClosedInterval(start: Date, end: Date) extends Interval
case class OpenInterval(start: Date) extends Interval {
  def close = ClosedInterval(start, new Date(start.getTime + 60000))
}

object TimetrackerAggregator {
  def main(args: Array[String]) {
    val lines = Source.fromFile("/Users/deak/.timetracker/timetracker.log").
                       getLines().
                       filter(_ matches """\d\d\d\d-\d\d-\d\d \d\d:\d\d:\d\d [A-Z][A-Z][A-Z]\t[0-4](\t\d+)?""").
                       map(Line.apply).
                       filter(_.powerState == PowerState.Working)
                       
    val thresh = 90000

    val intervals = lines.map(_.date).foldLeft(List.empty[Interval]){ 
      case (Nil, d)                                                        => List(OpenInterval(d))
      case (OpenInterval(start) :: rest, d) if same(d, start, thresh)      => ClosedInterval(start, d) :: rest
      case ((i@OpenInterval(start)) :: rest, d)                            => OpenInterval(d) :: i.close :: rest
      case (ClosedInterval(start, end) :: rest, d) if same(d, end, thresh) => ClosedInterval(start, d) :: rest
      case ((i@ClosedInterval(start, end)) :: rest, d)                     => OpenInterval(d) :: i :: rest
    }
    
    val finalizedIntervals = (intervals match {
      case OpenInterval(start) :: rest => ClosedInterval(start, new Date(start.getTime + 60000)) :: rest
      case d => d
    }).reverse
    
    finalizedIntervals foreach println
  }
  
  def same(newer: Date, older: Date, thresh: Int) = 
    newer.getTime - older.getTime < thresh
}

TimetrackerAggregator.main(args)