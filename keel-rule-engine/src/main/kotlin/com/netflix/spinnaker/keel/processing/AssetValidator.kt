package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.fingerprint
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Missing
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AssetValidator(
  private val repository: AssetRepository,
  private val assetService: AssetService
) {
  private val log = LoggerFactory.getLogger(javaClass)

  // TODO: coroutine
  fun validateSubTree(id: AssetId): Set<AssetId> {
    val desired = repository.get(id)
    if (desired == null) {
      log.error("{}: Not found", id)
      return emptySet()
    } else {
      val invalidAssetIds = mutableSetOf<AssetId>()
      log.info("{}: Validating state", id)
      assetService
        .current(desired)
        .also { current ->
          when {
            current == null -> {
              log.debug("{}: Does not exist", id)
              repository.updateState(id, Missing)
              invalidAssetIds.add(id)
            }
            desired.fingerprint == current.fingerprint -> {
              log.debug("{}: Current state valid", id)
              repository.updateState(id, Ok)
            }
            else -> {
              log.info("{}: Current state invalid", id)
              repository.updateState(id, Diff)
              invalidAssetIds.add(id)
            }
          }
          repository.dependents(id).forEach { dependentId ->
            log.debug("{}: Validating dependent asset {}", id, dependentId)
            validateSubTree(dependentId).also { them ->
              invalidAssetIds.addAll(them)
            }
          }
        }
      return invalidAssetIds
    }
  }
}