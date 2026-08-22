# API clients and curl examples

Register:
```bash
curl -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{"name":"Test","email":"test@example.com","password":"password"}'
```

Login (returns token):
```bash
curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"test@example.com","password":"password"}'
# save token: export TOKEN=$(jq -r .token response.json)
```

Create application:
```bash
curl -X POST http://localhost:8080/api/applications -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"companyName":"Acme","roleTitle":"SDE","jobDescription":"Java developer","status":"APPLIED"}'
```

Upload resume:
```bash
curl -X POST http://localhost:8080/api/resume/upload -H "Authorization: Bearer $TOKEN" -F "file=@/path/to/resume.pdf"
```

Match score:
```bash
curl -X POST http://localhost:8080/api/match/score -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"resumeId":1,"jobDescriptionText":"Java Spring Boot"}'
```
