FROM tomcat:9-jre8
MAINTAINER Mircea Dobreanu (github.com/mcdobr)

# iceweasel is Debian-speak for firefox
RUN rm -rf /usr/local/tomcat/webapps/* && \
	apt-get update && \
	apt-get install -y --no-install-recommends ca-certificates curl iceweasel  && \
	curl -L https://github.com/mozilla/geckodriver/releases/download/v0.24.0/geckodriver-v0.24.0-linux64.tar.gz | tar xz -C /opt
CMD ["catalina.sh", "run"]
COPY ./target/crawler.war $CATALINA_HOME/webapps
COPY ./geckodriver /opt/
