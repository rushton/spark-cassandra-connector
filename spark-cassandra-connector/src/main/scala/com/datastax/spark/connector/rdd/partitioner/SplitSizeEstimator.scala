package com.datastax.spark.connector.rdd.partitioner

import com.datastax.spark.connector.rdd.CassandraRDD
import com.datastax.spark.connector.rdd.partitioner.dht.TokenFactory

private[rdd] trait SplitSizeEstimator[R] {
  this: CassandraRDD[R] =>

  @transient implicit lazy val tokenFactory = TokenFactory.forSystemLocalPartitioner(connector)

  private def estimateDataSize: Long =
    new DataSizeEstimates(connector, keyspaceName, tableName).dataSizeInBytes

  private[rdd] def minimalSplitCount: Int = {
    val coreCount = context.defaultParallelism
    1 + coreCount * 2
  }

  def estimateSplitCount(splitSize: Long): Int = {
    require(splitSize > 0, "Split size must be greater than zero.")
    if (estimateDataSize == Long.MaxValue || estimateDataSize < 0) {
      logWarning(
        s"""Size Estimates has overflowed and calculated that the data size is Infinite.
        |Falling back to $minimalSplitCount (2 * SparkCores + 1) Split Count.
        |This is most likely occurring because you are reading size_estimates
        |from a DataCenter which has very small primary ranges. Explicitly set
        |the splitCount when reading to manually adjust this.""".stripMargin)
      minimalSplitCount
    } else {
      val splitCountEstimate = estimateDataSize / splitSize
      Math.max(splitCountEstimate.toInt, minimalSplitCount)
    }
  }

}
