package filodb.query.exec.rangefn

import scala.util.Random

import com.typesafe.config.{Config, ConfigFactory}
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures

import filodb.core.MetricsTestData
import filodb.core.query.{CustomRangeVectorKey, RangeVector, RangeVectorKey, ResultSchema}
import filodb.memory.format.{RowReader, ZeroCopyUTF8String}
import filodb.query._
import filodb.query.exec.TransientRow

class BinaryOperatorSpec extends FunSpec with Matchers with ScalaFutures {

  val config: Config = ConfigFactory.load("application_test.conf").getConfig("filodb")
  val resultSchema = ResultSchema(MetricsTestData.timeseriesDataset.infosFromIDs(0 to 1), 1)
  val ignoreKey = CustomRangeVectorKey(
    Map(ZeroCopyUTF8String("ignore") -> ZeroCopyUTF8String("ignore")))
  val sampleBase: Array[RangeVector] = Array(
    new RangeVector {
      override def key: RangeVectorKey = ignoreKey
      override def rows: Iterator[RowReader] = Seq(
        new TransientRow(1L, 3.3d),
        new TransientRow(2L, 5.1d)).iterator
    },
    new RangeVector {
      override def key: RangeVectorKey = ignoreKey
      override def rows: Iterator[RowReader] = Seq(
        new TransientRow(3L, 3239.3423d),
        new TransientRow(4L, 94935.1523d)).iterator
    })
  val queryConfig = new QueryConfig(config.getConfig("query"))
  val rand = new Random()
  val error = 0.00000001d
  val scalar = 5.0

  it("should work with Binary Operator mapper") {
    val ignoreKey = CustomRangeVectorKey(
      Map(ZeroCopyUTF8String("ignore") -> ZeroCopyUTF8String("ignore")))

    val samples: Array[RangeVector] = Array.fill(100)(new RangeVector {
      val data: Stream[TransientRow] = Stream.from(0).map { n =>
        new TransientRow(n.toLong, rand.nextDouble())
      }.take(20)

      override def key: RangeVectorKey = ignoreKey

      override def rows: Iterator[RowReader] = data.iterator
    })
    fireBinaryOperatorTests(samples)
  }

  it ("should handle NaN") {
    val ignoreKey = CustomRangeVectorKey(
      Map(ZeroCopyUTF8String("ignore") -> ZeroCopyUTF8String("ignore")))

    val samples: Array[RangeVector] = Array(
      new RangeVector {
        override def key: RangeVectorKey = ignoreKey
        override def rows: Iterator[RowReader] = Seq(
          new TransientRow(1L, Double.NaN),
          new TransientRow(2L, 5.6d)).iterator
      },
      new RangeVector {
        override def key: RangeVectorKey = ignoreKey
        override def rows: Iterator[RowReader] = Seq(
          new TransientRow(1L, 4.6d),
          new TransientRow(2L, 4.4d)).iterator
      },
      new RangeVector {
        override def key: RangeVectorKey = ignoreKey
        override def rows: Iterator[RowReader] = Seq(
          new TransientRow(1L, 0d),
          new TransientRow(2L, 5.4d)).iterator
      }
    )
    fireBinaryOperatorTests(samples)
  }

  it ("should handle special cases") {
    val ignoreKey = CustomRangeVectorKey(
      Map(ZeroCopyUTF8String("ignore") -> ZeroCopyUTF8String("ignore")))

    val samples: Array[RangeVector] = Array(
      new RangeVector {
        override def key: RangeVectorKey = ignoreKey

        override def rows: Iterator[RowReader] = Seq(
          new TransientRow(1L, 2.0d/0d),
          new TransientRow(2L, 4.5d),
          new TransientRow(2L, 0d),
          new TransientRow(2L, -2.1d),
          new TransientRow(2L, 5.9d),
          new TransientRow(2L, Double.NaN),
          new TransientRow(2L, 3.3d)).iterator
      }
    )
    fireBinaryOperatorTests(samples)
  }

  private def fireBinaryOperatorTests(samples: Array[RangeVector]): Unit = {

    // Subtraction - prefix
    val expectedSub1 = samples.map(_.rows.map(v => scalar - v.getDouble(1)))
    applyBinaryOperationAndAssertResult(samples, expectedSub1, BinaryOperator.SUB, scalar, true)

    // Subtraction - suffix
    val expectedSub2 = samples.map(_.rows.map(v => v.getDouble(1) - scalar))
    applyBinaryOperationAndAssertResult(samples, expectedSub2, BinaryOperator.SUB, scalar, false)

    // Addition - prefix
    val expectedAdd1 = samples.map(_.rows.map(v => scalar + v.getDouble(1)))
    applyBinaryOperationAndAssertResult(samples, expectedAdd1, BinaryOperator.ADD, scalar, true)

    // Addition - suffix
    val expectedAdd2 = samples.map(_.rows.map(v => v.getDouble(1) + scalar))
    applyBinaryOperationAndAssertResult(samples, expectedAdd2, BinaryOperator.ADD, scalar, false)

    // Multiply - prefix
    val expectedMul1 = samples.map(_.rows.map(v => scalar * v.getDouble(1)))
    applyBinaryOperationAndAssertResult(samples, expectedMul1, BinaryOperator.MUL, scalar, true)

    // Multiply - suffix
    val expectedMul2 = samples.map(_.rows.map(v => v.getDouble(1) * scalar))
    applyBinaryOperationAndAssertResult(samples, expectedMul2, BinaryOperator.MUL, scalar, false)

    // Modulo - prefix
    val expectedMod1 = samples.map(_.rows.map(v => scalar % v.getDouble(1)))
    applyBinaryOperationAndAssertResult(samples, expectedMod1, BinaryOperator.MOD, scalar, true)

    // Modulo - suffix
    val expectedMod2 = samples.map(_.rows.map(v => v.getDouble(1) % scalar))
    applyBinaryOperationAndAssertResult(samples, expectedMod2, BinaryOperator.MOD, scalar, false)

    // Division - prefix
    val expectedDiv1 = samples.map(_.rows.map(v => scalar / v.getDouble(1)))
    applyBinaryOperationAndAssertResult(samples, expectedDiv1, BinaryOperator.DIV, scalar, true)

    // Division - suffix
    val expectedDiv2 = samples.map(_.rows.map(v => v.getDouble(1) / scalar))
    applyBinaryOperationAndAssertResult(samples, expectedDiv2, BinaryOperator.DIV, scalar, false)

    // power - prefix
    val expectedPow1 = samples.map(_.rows.map(v => math.pow(scalar, v.getDouble(1))))
    applyBinaryOperationAndAssertResult(samples, expectedPow1, BinaryOperator.POW, scalar, true)

    // power - suffix
    val expectedPow2 = samples.map(_.rows.map(v => math.pow(v.getDouble(1), scalar)))
    applyBinaryOperationAndAssertResult(samples, expectedPow2, BinaryOperator.POW, scalar, false)
  }

  it ("should handle unknown functions") {
    // sort_desc
    the[UnsupportedOperationException] thrownBy {
      val binaryOpMapper = exec.ScalarOperationMapper(BinaryOperator.EQL, 10, true)
      binaryOpMapper(Observable.fromIterable(sampleBase), queryConfig, 1000, resultSchema)
    } should have message "EQL not supported."

    the[UnsupportedOperationException] thrownBy {
      val binaryOpMapper = exec.ScalarOperationMapper(BinaryOperator.GTE, 10, false)
      binaryOpMapper(Observable.fromIterable(sampleBase), queryConfig, 1000, resultSchema)
    } should have message "GTE not supported."
  }

  it ("should fail with wrong calculation") {
    // ceil
    val expectedVal = sampleBase.map(_.rows.map(v => scala.math.floor(v.getDouble(1))))
    val binaryOpMapper = exec.ScalarOperationMapper(BinaryOperator.ADD, scalar, true)
    val resultObs = binaryOpMapper(Observable.fromIterable(sampleBase), queryConfig, 1000, resultSchema)
    val result = resultObs.toListL.runAsync.futureValue.map(_.rows.map(_.getDouble(1)))
    expectedVal.zip(result).foreach {
      case (ex, res) =>  {
        ex.zip(res).foreach {
          case (val1, val2) =>
            val1 should not equal val2
        }
      }
    }
  }

  private def applyBinaryOperationAndAssertResult(samples: Array[RangeVector], expectedVal: Array[Iterator[Double]],
                                                  binOp: BinaryOperator, scalar: Double, scalarOnLhs: Boolean): Unit = {
    val scalarOpMapper = exec.ScalarOperationMapper(binOp, scalar, scalarOnLhs)
    val resultObs = scalarOpMapper(Observable.fromIterable(samples), queryConfig, 1000, resultSchema)
    val result = resultObs.toListL.runAsync.futureValue.map(_.rows.map(_.getDouble(1)))
    expectedVal.zip(result).foreach {
      case (ex, res) =>  {
        ex.zip(res).foreach {
          case (val1, val2) =>
            if (val1.isInfinity) val2.isInfinity shouldEqual true
            else if (val1.isNaN) val2.isNaN shouldEqual true
            else val1 shouldEqual val2
        }
      }
    }
  }

}