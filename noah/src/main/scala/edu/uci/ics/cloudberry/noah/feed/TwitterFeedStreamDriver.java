package edu.uci.ics.cloudberry.noah.feed;
import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.endpoint.Location;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import edu.uci.ics.cloudberry.noah.adm.UnknownPlaceException;
import org.kohsuke.args4j.CmdLineException;
import twitter4j.TwitterException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TwitterFeedStreamDriver {

    Client twitterClient;
    volatile boolean isConnected = false;
    FeedSocketAdapterClient socketAdapterClient;

    public void openSocket(Config config) throws IOException, CmdLineException {
        if(config.getPort() != 0 && config.getAdapterUrl()!=null) {
            if (!config.getIsFileOnly()) {
                String adapterUrl = config.getAdapterUrl();
                int port = config.getPort();
                int batchSize = config.getBatchSize();
                int waitMillSecPerRecord = config.getWaitMillSecPerRecord();
                int maxCount = config.getMaxCount();
                socketAdapterClient = new FeedSocketAdapterClient(adapterUrl, port,
                        batchSize, waitMillSecPerRecord, maxCount);
                socketAdapterClient.initialize();
            }
        }else{
            throw new CmdLineException("You should provide a port and an URL");
        }
    }

    public void run(Config config, BufferedWriter bw)
            throws InterruptedException, IOException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<String>(10000);
        StatusesFilterEndpoint endpoint = new StatusesFilterEndpoint();
        // add some track terms

        if (config.getTrackTerms().length != 0) {
            System.err.print("set track terms are: ");
            for (String term : config.getTrackTerms()) {
                System.err.print(term);
                System.err.print(" ");
            }
            System.err.println();
            endpoint.trackTerms(Lists.newArrayList(config.getTrackTerms()));
        }
        if (config.getTrackLocation().length != 0) {

            System.err.print("set track locations are:");
            for (Location location : config.getTrackLocation()) {
                System.err.print(location);
                System.err.print(" ");
            }
            System.err.println();

            endpoint.locations(Lists.<Location>newArrayList(config.getTrackLocation()));
        }

        Authentication auth = new OAuth1(config.getConsumerKey(), config.getConsumerSecret(), config.getToken(),
                config.getTokenSecret());

        // Create a new BasicClient. By default gzip is enabled.
        twitterClient = new ClientBuilder()
                .hosts(Constants.STREAM_HOST)
                .endpoint(endpoint)
                .authentication(auth)
                .processor(new StringDelimitedProcessor(queue))
                .build();


        // Establish a connection
        try {
            twitterClient.connect();
            isConnected = true;
            // Do whatever needs to be done with messages
            while (!twitterClient.isDone()) {
                String msg = queue.take();
                bw.write(msg);
                //if is not to store in file only, geo tag and send to database
                if (!config.getIsFileOnly()) {
                    try {
                        String adm = TagTweet.tagOneTweet(msg);
                        socketAdapterClient.ingest(adm);
                    } catch (UnknownPlaceException e) {

                    } catch (TwitterException e) {
                    }
                }
            }
        } finally {
            bw.close();
            twitterClient.stop();
        }
    }

    public static void main(String[] args) throws IOException {
        TwitterFeedStreamDriver feedDriver = new TwitterFeedStreamDriver();

        try {
            Config config = CmdLineAux.parseCmdLine(args);
            BufferedWriter bw = CmdLineAux.createWriter("Tweet_");

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    if (feedDriver.twitterClient != null && feedDriver.isConnected) {
                        feedDriver.twitterClient.stop();
                    }
                }
            });

            if (config.getTrackTerms().length == 0 && config.getTrackLocation().length == 0) {
                throw new CmdLineException("Should provide at least one tracking word, or one location boundary");
            }
            feedDriver.openSocket(config);
            feedDriver.run(config, bw);
        } catch (CmdLineException e) {
            System.err.println(e);

        } catch (InterruptedException e) {
            System.err.println(e);
        } finally {
            if (feedDriver.socketAdapterClient != null) {
                feedDriver.socketAdapterClient.finalize();
            }
        }
    }

}