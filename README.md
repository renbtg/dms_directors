# DOMUS Popular Directors

* Goal: load high remote-endpoint movies at start up, while allowing 
the endpoint /directors?threshold to return partially read data
* Can be configured by properties to use H2-db persistence. When
populardirectors.enable_persistence=false, then initial page fetching
is slower due to DB-saves, but application restarts are faster (fetching
only the pages still not saved)
* Please notice that the dms-hugemock application aims to generate
hundreds of thousands or even millions of movie entries (mimmicking 
IMDB contents, where > 650 k movies exist, scaling to +- 10 million
if we take into account all TV series episodes).
* We're assuming that the real problem to be solved is to deal with
such high movie mumbers with acceptable performance.
  * Virtual threads are used to deal with page fetching

Java version: 21

Building: mvn clean install

Running: java -Xmx3072 -jar target/popular-directors.jar

Known logging issue: log not being written, but console gets log messages
* nohup java -Xmx3072 -jar target/popular-directors.jar >output.log 2>&1 &
* tail -f output.log