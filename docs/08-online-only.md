# 08 · Work entirely in your browser

**No Java, Maven, GitHub Desktop, Docker or editor installation on your Mac is required.** GitHub Codespaces runs the tools and application on a cloud computer; your browser displays the editor and dashboard.

The Java/Maven copies previously prepared on this Mac are project-local files, not system-wide installations. The local app has been stopped. The existing files are retained so no source or demonstration data is lost.

## Current status

The project has now been rebuilt and verified in GitHub Codespaces on Linux with Java 17: 19 tests passed, with zero failures or errors. See docs/evidence/cloud-build-summary.txt.

For this prepared workspace, open https://improved-yodel-jj79wr9xvxx9hpvp.github.dev/ while signed in to the owning GitHub account. Other users should create their own codespace from the project branch.

## Path A: Once the prepared branch is on GitHub

1. Sign in to GitHub with the invited account.
2. Open the repository and select `codex/stockbridge-inventory` in the branch dropdown.
3. Click the green **Code** button, then **Codespaces**.
4. Create a codespace on this branch using the smallest available machine (normally 2 cores).
5. Choose the browser editor if asked. Wait for setup to finish. The included configuration installs Java 17 in the cloud and builds the application there.
6. In the editor, choose **Terminal → New Terminal**.
7. Paste this command and press Return:

   ```sh
   bash scripts/run-online.sh
   ```

8. Open the **Ports** tab near the terminal. For port **8080**, click **Open in Browser** (the globe icon). If the port is absent, use **Add Port** and enter `8080`.
9. Keep **Port Visibility** set to **Private**. The application does not have its own login.
10. The dashboard opens at a GitHub-generated address. Use that address instead of `localhost:8080` on your Mac.

## Path B: Upload the prepared project through the browser

Use this route if the GitHub repository still shows only the original starter.

1. Open the original repository in GitHub, click **Code → Codespaces**, and create a codespace on `main`. This creates a workspace; it does not change `main`.
2. Obtain the updated `stockbridge-source.zip` from the conversation. Downloading this small file is not installing software.
3. In the Codespaces browser editor's **Explorer**, right-click the repository folder and choose **Upload…**. Upload `stockbridge-source.zip` to the repository root, beside `pom.xml`. If your editor offers a different upload control, use it for the same destination.
4. Choose **Terminal → New Terminal**. Run:

   ```sh
   git switch -c codex/stockbridge-inventory
   ```

   If the branch already exists, use `git switch codex/stockbridge-inventory` instead.

5. Paste this entire block in that **cloud terminal**, not your Mac's Terminal:

   ```sh
   python3 - <<'PY'
   from pathlib import Path
   from zipfile import ZipFile
   archive = Path('stockbridge-source.zip')
   with ZipFile(archive) as bundle:
       for item in bundle.infolist():
           if item.is_dir():
               continue
           parts = Path(item.filename).parts
           if not parts or parts[0] != 'stockbridge' or len(parts) < 2:
               raise ValueError('Unexpected archive layout')
           relative = Path(*parts[1:])
           if '..' in relative.parts or '.git' in relative.parts:
               raise ValueError('Unsafe archive path')
           relative.parent.mkdir(parents=True, exist_ok=True)
           relative.write_bytes(bundle.read(item))
   duplicate = Path('src/main/src/main/resources/application.properties')
   if duplicate.exists():
       duplicate.unlink()
   archive.unlink()
   print('Project files are in place. Rebuild the container next.')
   PY
   ```

6. Press **Command+Shift+P** in the browser editor, search for **Codespaces: Rebuild Container**, and choose it. Confirm the normal rebuild. This uses the new `.devcontainer/devcontainer.json` to prepare Java in the cloud.
7. When setup finishes, run `bash scripts/run-online.sh` and open port 8080 as in Path A.

## Test and save your online work

Open a second terminal in the browser editor and run:

```sh
bash mvnw --batch-mode clean verify
```

For best results, stop the running app with Control+C before a clean build, then restart it afterwards. Look for `BUILD SUCCESS`. The Cucumber report is in `target/cucumber-report.html`.

To send the prepared source to GitHub from this cloud workspace:

```sh
git add .
git commit -m "Build StockBridge SOAP inventory and order management"
git push -u origin codex/stockbridge-inventory
```

If GitHub denies write access, use a fork under your account instead; do not change repository permissions. Once pushed, use GitHub's **Compare & pull request** and copy the text from `docs/PR-DESCRIPTION.md`. The branch page changes after pushing; `main` changes after the pull request is merged.

## Finish a session

1. Save your edits and commit/push source changes you want kept in GitHub.
2. Stop the Java app with Control+C in its terminal.
3. Open [your Codespaces page](https://github.com/codespaces), use the workspace's **…** menu, and choose **Stop codespace**. Closing a browser tab alone is not the same as stopping the cloud computer.

The H2 database lives inside the codespace's `data/` folder and is deliberately not committed. It normally survives stop/start of that same workspace, but deleting the codespace deletes that data. Codespaces is a development preview, not permanent public hosting.

## Usage and cost

Personal GitHub accounts have an included Codespaces allowance, not unlimited free use. Compute usage depends on the machine size; stored workspaces consume storage even when stopped. Check the billing owner and your account's remaining allowance before creating a workspace. Do not enable paid usage unless you choose to do so.

References: [What Codespaces is](https://docs.github.com/en/codespaces/about-codespaces/what-are-codespaces), [usage and billing](https://docs.github.com/en/billing/concepts/product-billing/github-codespaces), [opening forwarded ports](https://docs.github.com/en/codespaces/developing-in-a-codespace/forwarding-ports-in-your-codespace).
