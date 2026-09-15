# 05 · GitHub instructions for a beginner

**Updated preference: use the [browser-only guide](08-online-only.md). You do not need GitHub Desktop or local tools.** The instructions below remain an optional desktop route.

## The most important point

**You do not need to create Java files or paste code one file at a time. Everything is already in the correct place inside the `stockbridge` folder.** The folder is a local copy of Mr Zahir's repository with its original history and a separate branch called `codex/stockbridge-inventory`.

This section describes the original optional Mac workflow. For the current cloud workspace and publishing workflow, use docs/08-online-only.md.

## A. Add the prepared folder to GitHub Desktop

1. Install [GitHub Desktop](https://desktop.github.com/) if needed.
2. Open GitHub Desktop and sign in with the GitHub account Mr Zahir invited.
3. Choose **File → Add Local Repository**.
4. Click **Choose** and select this folder on your Mac:

   `/Users/dewaniftesham/Documents/Codex/2026-09-15/he/outputs/stockbridge`

5. Click **Add Repository**.
6. Confirm **Current Branch** says `codex/stockbridge-inventory`.

Do not choose `src`, `outputs`, or the ZIP file. Choose the complete `stockbridge` folder. If a Finder chooser is difficult to navigate, press Command+Shift+G and paste the folder path above.

## B. Save the changes as one commit

1. In GitHub Desktop, select the **Changes** tab.
2. The new and changed source, documentation, scripts and workflow files should be checked.
3. In the Summary box, type:

   `Build StockBridge SOAP inventory and order management application`

4. In the Description box, type:

   `Add transactional stock management, a browser dashboard, an XSD-generated SOAP service, Cucumber and integration tests, and project documentation.`

5. Click **Commit to codex/stockbridge-inventory**.

The ignored `.tools`, `data` and `target` folders should not appear in the commit. They contain downloaded tools, local data and build output. Do not force-add them.

## C. Send the branch to GitHub

1. Click **Publish branch** (or **Push origin**, if that is the button shown).
2. If you have write access, the branch will be published to Mr Zahir's repository.
3. If GitHub Desktop asks you to create a fork because you lack write access, choose **Fork this repository**, then **To contribute to the parent project**, and continue. Your changes will be on your own copy.
4. Open the repository on GitHub. Choose the `codex/stockbridge-inventory` branch and confirm the README says StockBridge.

## D. Create a review request for Mr Zahir

1. On GitHub, use **Compare & pull request** for your published branch, or open **Pull requests → New pull request**.
2. Set the base repository to `zahir-imt/spring-soap-project`, base branch `main`, and the compare branch to your `codex/stockbridge-inventory` branch. If you used a fork, select your fork as the head repository.
3. Copy the title and description from `docs/PR-DESCRIPTION.md` into the corresponding fields. The first heading is the title; the text below it is the description.
4. Create the pull request. This proposes the changes for review; it does not merge them into `main`.
5. Open the **Actions** tab and check the build results. Forked pull requests may need a maintainer to approve the workflow before it runs.
6. If the workflow runs successfully, its `stockbridge-results` artifact contains the fresh test reports and application JAR.
7. Share the pull request link with Mr Zahir yourself.

## What goes where?

| Item | Correct location | What you do |
|---|---|---|
| Java source | `src/main/java/` | Already placed; leave it there |
| Web page and styling | `src/main/resources/static/` | Already placed |
| SOAP contract | `src/main/resources/xsd/inventory.xsd` | Already placed |
| Automated tests | `src/test/` | Already placed |
| Analysis and design | `docs/01-analysis.md`, `docs/02-design.md` | Already placed |
| CI workflow | `.github/workflows/build.yml` | Already placed, even though the folder is hidden in Finder |
| GitHub PR text | `docs/PR-DESCRIPTION.md` | Copy into the PR form |
| Application database | `data/` | Keep local; automatically ignored |

## If you only have the ZIP on another computer

The source ZIP excludes Git history and downloaded tools. Use GitHub Desktop to clone the original repository, create a new branch, then copy the ZIP's contents into the cloned repository root (the folder containing `pom.xml`). Show hidden files with Command+Shift+. on Mac so `.github`, `.mvn` and `.gitignore` are included. Do not copy or replace a `.git` folder. Continue with section B. The prepared folder on this Mac is the simpler route.

Official references: [Add a local repository](https://docs.github.com/en/desktop/adding-and-cloning-repositories/adding-a-repository-from-your-local-computer-to-github-desktop), [Forking in GitHub Desktop](https://docs.github.com/en/desktop/adding-and-cloning-repositories/cloning-and-forking-repositories-from-github-desktop).
