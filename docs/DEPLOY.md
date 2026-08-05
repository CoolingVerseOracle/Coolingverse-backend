# 백엔드 배포 절차 (OCI Compute)

백엔드를 OCI Compute 인스턴스에 올리는 전체 절차입니다.
처음 따라 하는 사람 기준으로 작성했으며, 프론트 정적 파일 서빙은 별도 단계(프론트 배포)에서 다룹니다.

> 준비물: OCI 콘솔 접근 권한, ADB Wallet 폴더, ADB ADMIN 비밀번호, 운영용 관리자 계정(팀 결정)

---

## 0. 사전 결정 사항

| 항목 | 내용 |
| --- | --- |
| 운영 관리자 계정 | 로컬 테스트 계정(coolingverse8) 재사용 금지 — 팀에서 새 ID/비밀번호 결정 |
| 비밀번호 해시 | `./gradlew bcrypt -Ppw='새비밀번호'` 실행 → 출력된 해시를 사용 (원문은 어디에도 저장 안 함) |

## 1. Compute 인스턴스 생성 (OCI 콘솔)

1. 메뉴 → **Compute → Instances → Create instance**
2. 컴파트먼트: `coolingverse`, 리전: ADB와 동일(South Korea North)
3. **Image**: Ubuntu 22.04 이상 / **Shape**: `VM.Standard.A1.Flex` (Ampere ARM)
   - OCPU 2 / 메모리 12GB 권장 — **"Always Free eligible" 라벨 확인 필수** (없으면 과금)
   - Ampere 용량 부족 오류 시: 시간대 바꿔 재시도, 안 되면 `VM.Standard.E2.1.Micro`(1GB)로 대체
4. **SSH 키**: "Generate a key pair" → 개인키(.key) 다운로드해 안전한 곳에 보관 (분실 시 접속 불가)
5. 생성 후 **Public IP 주소**를 기록해 둔다

## 2. 네트워크 방화벽 열기

OCI는 방화벽이 이중이다: ① 콘솔의 Security List ② OS 내부 방화벽. 둘 다 열어야 통한다.

**① 콘솔:** 인스턴스 상세 → Subnet 클릭 → Security List → **Add Ingress Rules**
- Source `0.0.0.0/0`, 프로토콜 TCP, 포트 `80` (nginx용)
- 22(SSH)는 기본으로 열려 있음

**② 서버 접속 후 (3단계 이후 실행):**
```bash
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo netfilter-persistent save   # 없으면: sudo apt install -y iptables-persistent
```

## 3. 서버 접속 (내 PC PowerShell에서)

```powershell
ssh -i C:\경로\다운받은키.key ubuntu@<Public_IP>
```

## 4. 서버 기본 설치

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless nginx
java -version   # 17 확인
```

## 5. 파일 업로드 (내 PC PowerShell에서)

```powershell
# 실행 파일 빌드 (레포 폴더에서)
.\gradlew.bat bootJar
# 생성 위치: build\libs\parking-auth-0.0.1-SNAPSHOT.jar

# 업로드 (jar 이름은 parking-auth.jar 로 통일)
scp -i C:\경로\키.key build\libs\parking-auth-0.0.1-SNAPSHOT.jar ubuntu@<IP>:/home/ubuntu/app/parking-auth.jar
scp -i C:\경로\키.key -r C:\oracle\wallet ubuntu@<IP>:/home/ubuntu/wallet
scp -i C:\경로\키.key deploy\coolingverse-backend.service deploy\nginx-api.conf ubuntu@<IP>:/home/ubuntu/
```
(먼저 서버에서 `mkdir -p /home/ubuntu/app` 실행)

## 6. 환경변수 등록 (서버에서)

```bash
sudo mkdir -p /etc/coolingverse
sudo nano /etc/coolingverse/backend.env    # deploy/backend.env.example 내용 참고해 실제 값 기입
sudo chmod 600 /etc/coolingverse/backend.env
```
필수 항목: `ADMIN_ID`, `ADMIN_PASSWORD_HASH`, `ADB_PASSWORD`, `ADB_WALLET_DIR=/home/ubuntu/wallet`

## 7. 서비스 등록 (자동 시작·자동 재시작)

```bash
sudo cp /home/ubuntu/coolingverse-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now coolingverse-backend
sudo journalctl -u coolingverse-backend -f    # "Started ParkingAuthApplication" 확인
```
로그에 `집계: DB 실측값 사용 — 미개방 유휴면 39114...` 가 보이면 ADB 연결 성공.

## 8. nginx 연결

```bash
sudo cp /home/ubuntu/nginx-api.conf /etc/nginx/sites-available/coolingverse
sudo ln -s /etc/nginx/sites-available/coolingverse /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

## 9. 검증 (내 PC에서)

```powershell
# 로그인이 성공하면 배포 완료
Invoke-RestMethod -Uri "http://<Public_IP>/api/login" -Method Post -ContentType "application/json" -Body '{"username":"운영ID","password":"운영비밀번호"}'
```

체크리스트:
- [ ] `/api/login` 성공 + 토큰 발급
- [ ] `/api/scenarios` 토큰 없이 401, 토큰으로 5건
- [ ] `/api/simulate/initial` KPI 5장
- [ ] 서버 재부팅(`sudo reboot`) 후 자동 기동 확인

## 10. 재배포 (코드 수정 후)

```powershell
.\gradlew.bat bootJar
scp -i C:\경로\키.key build\libs\parking-auth-0.0.1-SNAPSHOT.jar ubuntu@<IP>:/home/ubuntu/app/parking-auth.jar
ssh -i C:\경로\키.key ubuntu@<IP> "sudo systemctl restart coolingverse-backend"
```

## 주의사항

- **ADB 자동 정지**: Always Free ADB는 7일 미사용 시 자동 정지 → 발표 당일 콘솔에서 Available 확인
- **Wallet·env 파일은 절대 git에 올리지 않는다** (deploy/의 example만 저장소에 존재)
- 백엔드 재시작 = 로그인 토큰 전체 무효 (재로그인 필요) — 정상 동작
- 프론트 정적 파일 서빙은 프론트 빌드 완성 후 nginx-api.conf에 블록 추가 (별도 단계)
