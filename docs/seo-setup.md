# SEO / discoverability setup

Everything below is set in the **GitHub repo settings**
(`Settings → General`, `Settings → Topics`) — it cannot live in the repository
files. Apply it so the repo ranks for the right searches and links back to the
author **Bishow Pandey**.

## 1. Repo name & URL

Keep the repository name descriptive (keywords in the URL rank on GitHub):

```
cloud-sre-security-platform
```

## 2. Repo description (Settings → General)

```
Cloud SRE & Security Platform by Bishow Pandey — learn Site Reliability
Engineering, incident management, fault injection (chaos engineering),
Kubernetes (EKS/Kind), cloud security, observability (Prometheus, Grafana,
OpenTelemetry) and CI/CD security gates by breaking production on purpose.
Java Spring Boot 21 · React · Terraform · GitHub Actions
```

## 3. Topics (Settings → Topics — up to 20)

```
sre
site-reliability-engineering
incident-management
incident-response
chaos-engineering
fault-injection
kubernetes
eks
aws
java
spring-boot
devops
cloud-security
observability
prometheus
grafana
opentelemetry
terraform
github-actions
educational
```

## 4. Website link (Settings → General)

If you ever deploy the dashboard (nginx, GitHub Pages, or an ingress on EKS),
paste its URL into the **Website** field so GitHub surfaces it and the
`<link rel="canonical">` in `dashboard/index.html` matches it.

## 5. Social image

Add an `images/social-preview.png` (1280×640) and reference it from `README.md`
so links shared on social media get a card:

```markdown
![SRE Platform — Bishow Pandey](images/social-preview.png)
```

## 6. Release + tags

Push a `v1.0.0` tag with release notes (`Incidents, playbooks, rollback,
observability, k8s, terraform, CI/CD` in the title/body) — releases and tags
add indexable pages.

## What the repo files already do for SEO

| File | What it does |
|---|---|
| `README.md` | Keyword-rich intro (SRE, cloud security, Kubernetes, incident management, fault injection, observability, CI/CD, Terraform) + author credit — GitHub indexes the first ~200 lines heavily. |
| `dashboard/index.html` | `title`, `meta description`, `keywords`, `author` (Bishow Pandey), `robots`, `canonical`, Open Graph + Twitter cards, and `schema.org/WebApplication` structured data with author. |
| `docs/educational-guide.md` | Long-form keyword-dense educational content (ranks for "learn SRE", "SRE tutorial", etc.) with author attribution. |
| `docs/*`, `terraform/README.md` | Supporting indexable documentation pages. |
