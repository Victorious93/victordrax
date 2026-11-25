# Engagements & scope

An **engagement** is an authorized piece of work. Everything you do should belong to one.

## Authorization model
An engagement counts as *authorized* only when all three are present:

1. **Authorized by** — the name of the person/organization who authorized the test.
2. **Authorization date** — when it was authorized.
3. **Signed authorization document** — a file you attach (stored as a content:// URI via the Android file picker).

Until all three are set, the engagement shows as unauthorized and scope-gated actions are blocked.

## Scope entries
Scope defines the targets you are allowed to touch. A scope line can be:

- an exact host or IPv4 address — `example.com`, `10.0.0.5`
- a domain (matches it and any sub-domain) — `example.com` matches `api.example.com`
- a wildcard domain — `*.example.com`
- an IPv4 CIDR range — `10.0.0.0/24`

## In-scope checks
Before any tool, scan, workflow or scheduled run executes, VictorDrax checks the target against the engagement's scope. IPv4 targets match an exact IP or a containing CIDR; hostnames match exactly or as a subdomain; wildcard entries match strict subdomains. Anything out of scope is refused.
