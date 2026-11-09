/// <reference path="../pb_data/types.d.ts" />
// Zoomi sync server schema. The server never sees settings in plain text: the app encrypts them
// end-to-end (AES-256-GCM, key derived from the password on the phone) before upload.
migrate((app) => {
  // 1) Accounts: username + password only. No email, no name, no avatar.
  //    The "password" the server receives is itself derived on the phone (PBKDF2), not the real one.
  const accounts = new Collection({
    type: "auth",
    name: "accounts",
    listRule: "id = @request.auth.id",
    viewRule: "id = @request.auth.id",
    createRule: "",                          // anyone may register (rate-limited below)
    updateRule: null,                        // no profile edits via API
    deleteRule: "id = @request.auth.id",     // users can delete themselves (GDPR art. 17)
    fields: [
      { type: "text", name: "username", required: true, min: 3, max: 32, pattern: "^[a-z0-9_.-]+$" },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_accounts_username ON accounts (username)"],
    passwordAuth: { enabled: true, identityFields: ["username"] },
    oauth2: { enabled: false },
    otp: { enabled: false },
    mfa: { enabled: false },
  })
  app.save(accounts)

  // Email is a system field of auth collections; make it optional and never shown.
  const acc = app.findCollectionByNameOrId("accounts")
  const email = acc.fields.getByName("email")
  email.required = false
  email.hidden = true
  app.save(acc)

  // 2) One encrypted settings blob per account.
  const blobs = new Collection({
    type: "base",
    name: "sync_blobs",
    listRule: "account = @request.auth.id",
    viewRule: "account = @request.auth.id",
    createRule: "@request.auth.id != '' && account = @request.auth.id",
    updateRule: "account = @request.auth.id && @request.body.account:isset = false",
    deleteRule: "account = @request.auth.id",
    fields: [
      { type: "relation", name: "account", required: true, maxSelect: 1, collectionId: acc.id, cascadeDelete: true },
      { type: "text", name: "ciphertext", required: true, max: 262144 },  // <= 256 KB
      { type: "number", name: "version", required: true, min: 1, onlyInt: true },
      { type: "autodate", name: "updated", onCreate: true, onUpdate: true },
    ],
    indexes: ["CREATE UNIQUE INDEX idx_sync_blobs_account ON sync_blobs (account)"],
  })
  app.save(blobs)

  // 3) Privacy + abuse settings.
  const s = app.settings()
  s.meta.appName = "Zoomi sync"
  s.logs.maxDays = 3          // keep request logs briefly (debugging), then delete
  s.logs.logIP = false        // never store client IP addresses
  s.logs.logAuthId = false    // don't link log lines to accounts
  s.rateLimits.enabled = true
  s.rateLimits.rules = [
    { label: "*:auth", maxRequests: 5, duration: 60, audience: "" },    // login attempts per IP per minute
    { label: "*:create", maxRequests: 10, duration: 60, audience: "" }, // registrations / blob creates
    { label: "/api/", maxRequests: 120, duration: 60, audience: "" },  // everything else
  ]
  app.save(s)
}, (app) => {
  for (const name of ["sync_blobs", "accounts"]) {
    try { app.delete(app.findCollectionByNameOrId(name)) } catch (_) {}
  }
})
