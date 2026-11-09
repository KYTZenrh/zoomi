/// <reference path="../pb_data/types.d.ts" />
// Optimistic concurrency for the encrypted settings blob: an update must carry exactly
// version = stored version + 1, otherwise another device wrote first and the client must
// re-download before uploading (prevents silent overwrites between two phones).
onRecordUpdateRequest((e) => {
  const stored = $app.findRecordById("sync_blobs", e.record.id)
  const expected = stored.getInt("version") + 1
  if (e.record.getInt("version") !== expected) {
    throw new BadRequestError("version_conflict", { expected: expected })
  }
  e.next()
}, "sync_blobs")

// New blobs always start at version 1.
onRecordCreateRequest((e) => {
  if (e.record.getInt("version") !== 1) {
    throw new BadRequestError("version must be 1 on create")
  }
  e.next()
}, "sync_blobs")
