# Security policy

Trackhop Bridge keeps the web sessions of the services it connects (cookies and tokens of YouTube
Music, Amazon Music, Spotify, Apple Music and the others), encrypted on the phone, and answers only
Trackhop signed with the same key. Reports about anything that could expose them are welcome.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting on this repository (*Security → Report a vulnerability*)
rather than a public issue. Please include the Trackhop Bridge and Trackhop versions, the service
involved and steps to reproduce. You will get a reply as soon as possible; fixes ship as a new release
here.

## What is in scope

- Session storage on the phone (cookies, tokens, the player headers kept for Amazon Music)
- The interface to Trackhop: who may call it, and what an app other than Trackhop could read through it
- The sign-in screens (WebViews) and anything that could send a session to a third party
- Logs, diagnostic reports and traffic captures that could carry a token or a cookie

## What is not a vulnerability

- A service refusing or changing its web interface (that is a [bug report](https://github.com/xlollx/Trackhop-Bridge/issues/new/choose))
- Rate limits or quota errors returned by the music services

## Where the code lives

`android/bridge` and `android/ytm` are copies of Trackhop's modules (see `android/UPSTREAM`): a fix
there is made in [Trackhop](https://github.com/xlollx/Trackhop) and synced here the same day.

## Keys and secrets

No API key, signing key or session is stored in this repository. They are injected at build time
through GitHub Actions secrets. If you find one committed by mistake, report it as above.
