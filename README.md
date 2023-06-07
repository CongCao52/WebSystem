# WebSystem
## Compiling:
```bash
Ranking: javac --source-path src src/cis5550/ranking/Ranking.java
Frontend: javac --source-path src src/cis5550/frontend/Frontend.java
KVSMaster: javac --source-path src src/cis5550/kvs/Master.java
KVSWorker: javac --source-path src src/cis5550/kvs/Worker.java
```

## Running:
```bash
KVSMaster: java -cp src cis5550.kvs.Master 8000 //8000 is the port number the KVSMaster listening to
KVSWorker: java -cp src cis5550.kvs.Worker 9000 0.0.0.0:8000 //9000 is the port number the KVSWorker listening to, 0.0.0.0:8000 is the KVSMaster's ip:port
Ranking: java -cp src cis5550.ranking.Ranking 9010 0.0.0.0:8000 //9010 is the port number the Ranking listening to, 0.0.0.0:8000 is the KVSMaster's ip:port
Frontend: java -cp src cis5550.frontend.Frontend 0.0.0.0 443 1.1.1.1:9010 //0.0.0.0 is the ip of the frontend server, 443 is the port number, 1.1.1.1:9010 is the ip:port of the ranking
```
## Reproduce other static files (crawl.table, index.table):
```bash
Crawler: 
        Compile: javac -cp lib/jsoup-1.15.4.jar src/cis5550/tools/*.java src/cis5550/jobs/Crawler.java src/cis5550/kvs/*.java src/cis5550/generic/*.java src/cis5550/webserver/*.java src/cis5550/flame/*.java
        Create job: jar cvf cis5550/jobs/crawler.jar cis5550/jobs/Crawler.class  cis5550/tools/*.class cis5550/generic/*.class cis5550/kvs/*.class cis5550/webserver/*.class cis5550/flame/*.class
Indexer:
        Compile: javac -cp lib/jsoup-1.15.4.jar src/cis5550/tools/*.java src/cis5550/jobs/Indexer.java src/cis5550/kvs/*.java src/cis5550/generic/*.java src/cis5550/webserver/*.java src/cis5550/flame/*.java
        Create job: jar cvf cis5550/jobs/Indexer.jar cis5550/jobs/Indexer.class  cis5550/tools/*.class cis5550/generic/*.class cis5550/kvs/*.class cis5550/webserver/*.class cis5550/flame/*.class
Header:
        Compile: javac -cp lib/jsoup-1.15.4.jar src/cis5550/tools/*.java src/cis5550/jobs/Header.java src/cis5550/kvs/*.java src/cis5550/generic/*.java src/cis5550/webserver/*.java src/cis5550/flame/*.java
        Create job: jar cvf cis5550/jobs/Indexer.jar cis5550/jobs/Header.class  cis5550/tools/*.class cis5550/generic/*.class cis5550/kvs/*.class cis5550/webserver/*.class cis5550/flame/*.class
PageRank:
        Compile: javac -cp lib/jsoup-1.15.4.jar src/cis5550/tools/*.java src/cis5550/jobs/PageRank.java src/cis5550/kvs/*.java src/cis5550/generic/*.java src/cis5550/webserver/*.java src/cis5550/flame/*.java
        Create job: jar cvf cis5550/jobs/PageRank.jar cis5550/jobs/PageRank.class  cis5550/tools/*.class cis5550/generic/*.class cis5550/kvs/*.class cis5550/webserver/*.class cis5550/flame/*.class       
        KVSMaster: sudo java -cp lib/jsoup-1.15.4.jar:src cis5550.kvs.Master 8000
        KVSWorker: sudo java -cp lib/jsoup-1.15.4.jar:src cis5550.kvs.Worker 8001 worker1 localhost:8000
        FlameMaster: sudo java -cp lib/jsoup-1.15.4.jar:src  cis5550.flame.Master 9000 localhost:8000
        FlameWorker: sudo java -cp lib/jsoup-1.15.4.jar:src  cis5550.flame.Worker 9001 localhost:9000
        FlameSubmit: sudo java -cp lib/jsoup-1.15.4.jar:src  cis5550.flame.FlameSubmit localhost:9000 src/cis5550/jobs/crawler.jar cis5550.jobs.Crawler <seed link> <blacklist table name> <file name of addition seeds, e.g. seeds.txt>
```
