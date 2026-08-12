# 배포 및 CI/CD 절차 (OCI Compute)

배포 구조는 한 대의 OCI Compute에서 nginx가 `https://coolingverse.com`의 Vue 정적 파일을 제공하고, `/api` 요청만 Spring Boot로 리버스 프록시하는 방식이다. Cloudflare는 DNS-only, 인증서는 OCI Nginx의 Certbot이 관리한다. `main`에 병합되면 GitHub Actions가 마이그레이션·테스트·빌드·업로드·readiness 검사까지 수행한다.

> 준비물: OCI 콘솔 접근 권한, ADB Wallet, ADB ADMIN 비밀번호, 운영 관리자 계정, GitHub Actions용 SSH 키

## 자동화 흐름

| 시점 | 백엔드 | 프런트엔드 |
| --- | --- | --- |
| `main` 대상 PR | Gradle 테스트·실행 JAR 빌드 | lint·타입 검사·단위 테스트·프로덕션 빌드 |
| `main` 병합 | Flyway migration → JAR 업로드 → 재시작 → readiness | 정적 파일 업로드 → HTTPS 검증 → 릴리스 전환 |
| 검증 실패 | 직전 JAR 심볼릭 링크로 되돌리고 재시작 | 직전 정적 릴리스 링크 복구 |

두 저장소의 배포 작업은 각각 `production` 환경과 동시 실행 방지 규칙을 사용한다. 운영 환경에 승인 규칙을 설정하면 `main` 병합 후에도 승인 전까지 배포는 멈춘다.

## 1. OCI Compute 최초 준비

OCI 콘솔에서 ADB와 같은 리전의 Ubuntu 24.04 Compute를 생성한다. Security List와 OS 방화벽에서 `80`, `443`을 열고, Spring Boot `8080`은 외부에 열지 않는다. SSH `22`는 팀의 고정 IP만 허용한다.

서버에 접속해 런타임과 nginx를 설치한다.

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless nginx curl certbot python3-certbot-nginx
sudo install -d -o ubuntu -g ubuntu -m 0755 /home/ubuntu/app/releases
sudo install -d -o ubuntu -g www-data -m 2775 /var/www/coolingverse/releases
sudo install -d -o www-data -g www-data -m 0755 /var/www/certbot
```

## 2. ADB와 서비스 구성

로컬 백엔드 저장소에서 Wallet과 최초 서버 설정 파일을 업로드한다. Wallet의 압축을 서버에서 풀어도 되지만, 저장소·GitHub Actions에는 절대 넣지 않는다.

```bash
scp -i <SSH_키_경로> -r <Wallet_폴더> ubuntu@<호스트>:/home/ubuntu/wallet
scp -i <SSH_키_경로> deploy/coolingverse-backend.service deploy/nginx-api.conf \
  ubuntu@<호스트>:/tmp/
```

운영 비밀값은 서버에만 만든다.

```bash
sudo install -d -o root -g root -m 0700 /etc/coolingverse
sudo nano /etc/coolingverse/backend.env
sudo chmod 600 /etc/coolingverse/backend.env
```

`backend.env`에는 `deploy/backend.env.example`의 형식대로 `ADMIN_ID`, `ADMIN_PASSWORD_HASH`, `ADB_PASSWORD`, `ADB_WALLET_DIR=/home/ubuntu/wallet`을 넣는다. 운영 관리자 비밀번호 해시는 로컬에서 `./gradlew bcrypt -Ppw='새비밀번호'`로 만든다. 원문 비밀번호, Wallet, `backend.env`는 저장소나 GitHub Actions에 올리지 않는다.

서비스 정의를 등록하고 자동 시작만 활성화한다. 첫 **Backend CD**가 실행 JAR와 심볼릭 링크를 만든 뒤 서비스를 처음 기동하므로, JAR를 수동 업로드할 필요는 없다.

```bash
sudo cp /tmp/coolingverse-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable coolingverse-backend
```

## 3. Cloudflare DNS와 HTTPS

Cloudflare에 `A @ → 158.180.70.158`, `CNAME www → coolingverse.com`을 등록하고 두 레코드 모두 DNS-only로 둔다. 인증서가 없을 때는 bootstrap 설정을 먼저 설치한다.

```bash
sudo cp deploy/nginx-bootstrap.conf /etc/nginx/sites-available/coolingverse
sudo ln -sfn /etc/nginx/sites-available/coolingverse /etc/nginx/sites-enabled/coolingverse
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
sudo certbot certonly --webroot -w /var/www/certbot -d coolingverse.com -d www.coolingverse.com
sudo cp deploy/nginx-api.conf /etc/nginx/sites-available/coolingverse
sudo nginx -t && sudo systemctl reload nginx
sudo systemctl enable --now certbot.timer
sudo certbot renew --dry-run
```

프런트 CD를 처음 실행하면 `/var/www/coolingverse/current`가 자동 생성돼 실제 정적 파일을 가리킨다.

## 4. GitHub `production` 환경 비밀값

**두 앱 저장소 모두** Settings → Environments → `production`에 SSH 비밀값을 넣고, 백엔드에는 ADB migration 비밀값도 추가한다.

| 이름 | 값 |
| --- | --- |
| `DEPLOY_HOST` | OCI Compute 공인 IP 또는 도메인 |
| `DEPLOY_USER` | 배포 계정 (현재 구성은 `ubuntu`) |
| `DEPLOY_SSH_PRIVATE_KEY` | GitHub Actions 전용 SSH 개인키 전체 |
| `DEPLOY_KNOWN_HOSTS` | `ssh-keyscan -H <호스트>`의 결과 |
| `ADB_USERNAME`, `ADB_PASSWORD` | Flyway 전용 ADB 계정 |
| `ADB_SERVICE_NAME` | Wallet의 서비스명(예: `cvadb_tp`) |
| `ADB_WALLET_BASE64` | Wallet zip의 Base64 |
| `ADMIN_ID` | 운영 관리자 ID(로컬 테스트 기본값 사용 금지) |
| `ADMIN_PASSWORD_HASH` | 운영 비밀번호의 BCrypt 해시 |
| `CERTBOT_EMAIL` | Let's Encrypt 만료 알림 이메일 |

SSH 포트가 기본값과 다르면 같은 환경의 Variables에 `DEPLOY_PORT`를 넣는다. 배포 키의 공개키는 서버 배포 계정의 `~/.ssh/authorized_keys`에 추가한다.

배포 계정은 `sudo systemctl restart coolingverse-backend`만 비밀번호 없이 실행할 수 있어야 한다. `/etc/sudoers.d/coolingverse-deploy`에 아래 한 줄을 넣고 `sudo visudo -cf /etc/sudoers.d/coolingverse-deploy`로 검증한다.

```sudoers
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl restart coolingverse-backend
```

프런트 저장소에는 추가로 Repository variable `NAVER_MAP_CLIENT_ID`를 등록한다. 지도 키는 브라우저 번들에 포함되는 공개 식별자이므로 Secret이 아니라 Variable을 사용한다.

## 5. 첫 자동 배포와 확인

두 저장소의 CI/CD 설정을 `main`에 병합한다. GitHub Actions에서 **Backend CD**를 한 번 실행해 API를 올린 뒤 **Frontend CD**를 실행한다. 이후 `main` 병합마다 자동 배포된다.

```bash
curl -i -X POST https://coolingverse.com/api/login \
  -H 'Content-Type: application/json' \
  --data '{"username":"운영ID","password":"운영비밀번호"}'
```

다음 항목을 확인한다.

- `/api/login`에서 토큰이 발급된다.
- 토큰 없이 `/api/scenarios`는 `401`, 토큰을 포함하면 시나리오 목록이 반환된다.
- 대시보드에서 초기 KPI와 지도 데이터가 정상 표시된다.
- 서버 재부팅 뒤 `coolingverse-backend`가 자동 기동된다.
- `https://www.coolingverse.com`은 `https://coolingverse.com`으로 이동한다.
- `systemctl list-timers certbot.timer`와 `certbot renew --dry-run`이 성공한다.

## 운영 주의사항

- Always Free ADB는 7일 동안 사용하지 않으면 자동 정지될 수 있으므로 시연 전 Available 상태를 확인한다.
- 백엔드 재시작 시 메모리 토큰은 모두 무효화된다. 이는 현재 인증 설계의 정상 동작이다.
- 네이버 지도 콘솔 서비스 URL에 `https://coolingverse.com`을 등록한다.
