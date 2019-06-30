[![Build Status](https://travis-ci.com/mcdobr/licenta-crawler.svg?branch=master)](https://travis-ci.com/mcdobr/licenta-crawler)


Run locally
```
mvn clean package -P prod -DskipTests
docker build -t gcr.io/bookworm-221210/crawler:latest .
docker run -p 8080:8080 -it gcr.io/bookworm-221210/crawler:latest
```