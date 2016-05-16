#!/bin/sh
exec scala "$0" "$@"
!#

import scala.io.Source
import java.text.SimpleDateFormat
import java.io.{PrintWriter, FileWriter, File}
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

object Constants {
  /**
   * Period in milliseconds for com.github.deaktator.timetracker.collector launch agent.
   */
  val CollectInterval = 60000 

  /** 1.95x CollectInterval */
  val AggTolerance = (1.95 * CollectInterval).round.toInt
}

case class Line(date: Date, powerState: PowerState, millisSinceTickle: Option[Int])

object Line {
  def apply(s: String): Line = {
    s.split("\t") match {
      case Array(date, powerState, millisSinceTickle) => 
        new Line(DateFormat.parse(date), PowerState(powerState.toInt), Option(millisSinceTickle.toInt))
      case Array(date, powerState) => 
        new Line(DateFormat.parse(date), PowerState(powerState.toInt), None)
    }
  }

  /** Same format as in timetracker_collector.sh */
  val DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
}

sealed trait Interval {
  val start: Date
  def close: ClosedInterval
  def closeWith(end: Date) = ClosedInterval(start, end)
  def includes(date: Date, thresh: Int = Constants.AggTolerance): Boolean
}

case class ClosedInterval(start: Date, end: Date) extends Interval {
  /** Return new ClosedInterval with start and end = end + Constants.CollectInterval. */
  def close = closeWith(new Date(end.getTime + Constants.CollectInterval))
  def includes(date: Date, thresh: Int = Constants.AggTolerance): Boolean =
    date.getTime - end.getTime < thresh
}

case class OpenInterval(start: Date) extends Interval {
  /** Create a ClosedInterval with start and end = start + Constants.CollectInterval. */
  def close = closeWith(new Date(start.getTime + Constants.CollectInterval))
  def includes(date: Date, thresh: Int = Constants.AggTolerance): Boolean =
    date.getTime - start.getTime < thresh
}

object TimetrackerAggregator {
  /** Same format as in timetracker_collector.sh */
  val TimeStampFormat = """^\d\d\d\d-\d\d-\d\d \d\d:\d\d:\d\d [A-Z][A-Z][A-Z]\t[0-4](\t\d+)?$"""
  val FileNameFormat = """^20\d{6}\.log$"""
  val FileDateFormat = new SimpleDateFormat("yyyyMMdd")
  val AcceptablePowerStates = Set(PowerState.Working, PowerState.PowerOnSuspend)

  /**
   * Main entry point to script.
   * @param args
   */
  def main(args: Array[String]): Unit = {
    val date = new Date
    val rawDir = new File(args(0))
    val processedDir = new File(args(1))
    val deleteRaw = if (3 == args.length) args(2).toBoolean else false
    process(rawDir, processedDir, date, deleteRaw)
  }

  def aggregatePreviousData(rawDir: File, date: Date): Iterator[(File, String)] = {
    val dateStr = FileDateFormat.format(date)

    val rawLogs = rawDir.listFiles().filter { f =>
      val name = f.getName
      name < dateStr && name.matches(FileNameFormat)
    }.toIterator

    rawLogs.map { f => (f, getAggContent(f)) }
  }

  def aggregateRaw(in: TraversableOnce[String]): List[Interval] = {
    val lines = in.toIterator.
      collect { case s if s matches TimeStampFormat => Line(s) }.
      collect { case s if AcceptablePowerStates contains s.powerState => s.date }

    val intervals = lines.foldLeft(List.empty[Interval]){
      case (Nil, d)                        => List(OpenInterval(d))
      case (i :: rest, d) if i.includes(d) => i.closeWith(d) :: rest
      case (i :: rest, d)                  => OpenInterval(d) :: i.close :: rest
    }

    (intervals match {
      case (i: Interval) :: rest => i.close :: rest // close the last interval.
      case d => d
    }).reverse
  }

  def getAggContent(in: File): String = {
    val lines = aggregateRaw(Source.fromFile(in).getLines())
    lines.collect { case ClosedInterval(s, e) => s"${Line.DateFormat.format(s)}\t${Line.DateFormat.format(e)}" }.
      mkString("\n")
  }

  // vvvvv  Impure function  vvvvv

  /**
    * Aggregate over all old raw files and created aggregated files.
    * @param rawDir directory with raw data
    * @param processedDir directory with aggregated data
    * @param date a date used for comparison.  We data data not from today.
    * @param deleteRaw whether to delete the raw data when done.
    */
  def process(rawDir: File, processedDir: File, date: Date, deleteRaw: Boolean = false) = {
    aggregatePreviousData(rawDir, date) foreach { case (in, data) =>
      writeAggregatedFile(in, data, processedDir)
      if (deleteRaw)
        in.delete()
    }
  }

  def writeAggregatedFile(in: File, data: String, processedDir: File): Unit = {
    val outFile = new File(processedDir, in.getName)
    outFile.delete()
    val out = new PrintWriter(new FileWriter(outFile, false))
    out.println(data)
    out.close()
  }
}

TimetrackerAggregator.main(args)
