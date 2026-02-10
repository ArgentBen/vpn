# Как отправить проект на GitHub (через Git Bash)

1. Откройте **Git Bash** (не PowerShell).

2. Перейдите в папку проекта:
   ```bash
   cd "/d/Заказы/ВПН"
   ```
   (В Git Bash диск `d:\` пишется как `/d/`.)

3. Инициализация репозитория (если ещё не делали):
   ```bash
   git init
   ```

4. Добавить удалённый репозиторий:
   ```bash
   git remote add origin https://github.com/ArgentBen/vpn.git
   ```
   Если `origin` уже есть и нужно заменить:
   ```bash
   git remote set-url origin https://github.com/ArgentBen/vpn.git
   ```

5. Добавить все файлы и сделать коммит:
   ```bash
   git add .
   git commit -m "VPN: V2Ray для ПК + Android-приложение с ss://"
   ```

6. Переименовать ветку в main и отправить:
   ```bash
   git branch -M main
   git push -u origin main
   ```

7. При первом `git push` Git может запросить логин и пароль. Используйте:
   - **логин:** ваш GitHub-ник (ArgentBen);
   - **пароль:** не пароль от аккаунта, а **Personal Access Token** (GitHub → Settings → Developer settings → Personal access tokens → создать токен с правом `repo`).

---

В проекте уже есть файл **.gitignore**: в репозиторий не попадут папки сборки, кэш Python и служебные файлы. Файлы `libv2ray.aar`, `geoip.dat`, `geosite.dat` и исходники — будут загружены.
