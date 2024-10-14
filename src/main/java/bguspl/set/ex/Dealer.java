package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.Random;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True if game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * array of the players Threads
     */
    private Thread[] playersThreads;

    /**
     * Queue of the Sets to check
     * this data structure requires synchronization 
     */
    private ArrayBlockingQueue<Set> setsToCheck;

    /**
     * Semaphore to protect setsToCheck
     */
    private Semaphore queueSafety;

    /**
     * Array that holds all the freeze time of the players
     */
    private long[] freezeArray;


    /**
     * boolean to reset game timer
     */
    private boolean reset = true;

    /**
     * Random object for choosing cards from deck
     */
    Random rand = new Random();

    /**
     * The thread representing the dealer.
     */
    private Thread dealer;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playersThreads = new Thread[players.length];
        setsToCheck = new ArrayBlockingQueue<Set>(players.length);
        queueSafety = new Semaphore(1, true);
        freezeArray = new long[players.length];
    }

    /**
     * interupt dealer thread
     */
    public void interruptDealer() {
        dealer.interrupt();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        dealer = Thread.currentThread();
        // Creating the players threads
        for (int i = 0; i < playersThreads.length; i++) {
            playersThreads[i] = new Thread(players[i],"player " + i);
        }

        //Starting the players threads
        for (int i = 0; i < playersThreads.length; i++) {
            playersThreads[i].start();
            try {
                Thread.sleep(50);
            } catch (Exception join) {//after each thread and ai created, dealer will get interupt
            }
        }

        //main loop of the dealer
        while (!shouldFinish()) {
                placeCardsOnTable();
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                timerLoop();
                updateTimerDisplay(true);
                removeAllCardsFromTable();
        }

        announceWinners();
        
            for (int i = playersThreads.length - 1; i >= 0; i--) {
                players[i].terminate();
                try {
                    playersThreads[i].join();
                } catch (InterruptedException ignore) {
                }
            }

        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
       }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            if (Thread.interrupted()) {
                // if true - Thread.interrupted will clear the thread flag
                int size = setsToCheck.size();
                int counter = 0;
                while (!terminate && counter<size){//v8
                    if(!checkSet())
                        updateTimerDisplay(reset);
                    counter++;
                    //v8 updateTimerDisplay(reset);
                }
            }

            if (System.currentTimeMillis() < reshuffleTime && !setsToCheck.isEmpty()){//v8
                checkSet();
            }

            if (System.currentTimeMillis() < reshuffleTime-env.config.turnTimeoutWarningMillis){//v8
                sleepUntilWokenOrTimeout();
            }

            updateTimerDisplay(reset);

            if (!terminate)
                checkToUnblock();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        blockPlayers();
        terminate = true;
      }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true if the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Removes 3 cards from The table
     */
    private void removeCardsFromTable(int[] toRemove) {
        table.removeCard(toRemove[0]);
        table.removeCard(toRemove[1]);
        table.removeCard(toRemove[2]);
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        for (int i = 0,deckSize=deck.size();i < 12 & deckSize>0; i++) {
            if (table.isTheSlotFree(i)) {
                int next = rand.nextInt(deckSize);
                //env.logger.info("next randon number:" + next);
                int tmp = deck.remove(next);
                table.placeCard(tmp, i);
                deckSize--;
            }
        }
        // if (!terminate){
        //     table.hints();
        //     System.out.println();}
        checkToUnblock();
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(env.config.turnTimeoutMillis/1000);
        } catch (InterruptedException check) {
            int size = setsToCheck.size();
            int counter = 0;
            while (!terminate && counter<size){//v8
                if(!checkSet())
                    updateTimerDisplay(reset);
                counter++;
            }
        }
    }

    /**
     * Unblocking the players according to freezeArray
     */
    private void checkToUnblock() {
        for (int i = 0; !terminate && i < freezeArray.length; i++) {
            if (freezeArray[i] <= System.currentTimeMillis()) {
                players[i].unblockPlayer();
                freezeArray[i] = 0;
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            this.reset = false;
        } else {
                if (reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis)
                    env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
                else {
                    env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
                }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        blockPlayers();
        for (int i = 0; i < 12 ; i++) {
            Integer card = table.getCardInSlot(i);
            if (card != null) {
                clearTokens(i);
                table.removeCard(i);
                deck.add(card);
            }
        }
        try {
            queueSafety.acquire();
        } catch (InterruptedException ignored) {
        }
        setsToCheck.clear();
        unblockPlayersSetInCheck();
        queueSafety.release();
    }

    /**
     * Clear all the players tokens from card in slot i 
     */
    private void clearTokens(int i) {
        for (Player player : players) {
            player.removeTokensByDealer(i);
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int[] scoreArray = new int[players.length];
        int max = 0;
        for (int i = 0; i < scoreArray.length; i++) {
            int tmpScore = players[i].score();
            scoreArray[i] = tmpScore;
            if(tmpScore>max){
                max = tmpScore;
            }
        }
        int winnerCounter = 0;
        for (int i = 0; i < scoreArray.length; i++) {
            if(scoreArray[i]==max){
                winnerCounter++;
            }
        }
        int[] winners = new int[winnerCounter];
        for (int i = 0; i < scoreArray.length; i++) {
            if(scoreArray[i]==max){
                winners[winnerCounter-1] = i;
                winnerCounter--;
            }
        }
        env.ui.announceWinner(winners);
    }

    /**
     * Blocks the players from accessing the table 
     */
    public void blockPlayers(){
        for (Player player : players) {
            player.blockPlayer();
        }
    }

    /**
     * Unblock players setInCheck boolean
     */
    public void unblockPlayersSetInCheck(){
        for (Player player : players) {
            player.setNoLongerValidforCheck();
        }
    }

    /**
     * Unblocks the players from accessing the table
     * @post - all players tableSafety equals to true
     */
    public void unblockPlayers(){
        for (Player player : players) {
            player.unblockPlayer();
        }
    }

    /**
     * Blocks player i from accessing the table 
     */
    public void block_Player(int i){
        players[i].blockPlayer();
    }

    /**
     * Unblocks player i from accessing the table 
     * 
     * @param i - index of player to unblock
     * 
     * @post - player i tableSafety equals to true
     */
    public void unblock_Player(int i){
        players[i].unblockPlayer();
    }

    /**
     * Adds player Set to checking queue
     * @param toCheck - player's set for dealer to check
     * 
     * @post - setsToCheck contains toCheck
     */
    public void addSetToCheck(Set toCheck){
        try {
            queueSafety.acquire();
        } catch (InterruptedException ignored) {
        }
        setsToCheck.add(toCheck);
        queueSafety.release();
        dealer.interrupt();
    }

    /**
     * Checks a Set from the checking queue
     */
    public boolean checkSet() {
        try {
            queueSafety.acquire();
        } catch (InterruptedException ignored) {
        }
        boolean validSetbool = false;
        if (!terminate && !setsToCheck.isEmpty()) {
            Set tmp = setsToCheck.remove();
            int[] toSend = convertToCards(tmp);
            queueSafety.release();
            validSetbool = env.util.testSet(toSend);
            if (validSetbool){
                int[] toDel = convertToArray(tmp);
                validSet(tmp.getId(), toDel);
            }
            else{
                notValidSet(tmp.getId());
                //for loger: int[] toDel = convertToArray(tmp);
                //env.logger.info("player " + (tmp.getId()+1) + " set is Not valid: "+toDel[0]+", "+toDel[1]+", "+toDel[2]);
            }
        }
        else{queueSafety.release();}
        return validSetbool;
    }

    /**
     * Converts Set of slots to an array of cards 
     */
    private int[] convertToCards(Set tmp){
        int[] toSend = new int[3];
        for (int i = 0; i < toSend.length; i++) {
            toSend[i] = table.getCardInSlot(tmp.getSlot(i));
        }
        return toSend;
    }

    /**
     * Converts Set of slots to an array of slots 
     */
    private int[] convertToArray(Set tmp){
        int[] toSend = new int[3];
        for (int i = 0; i < toSend.length; i++) {
            toSend[i] = tmp.getSlot(i);
        }
        return toSend;
    }

    /**
     * validSet functions continues the checkSet functions
     */
    private void validSet(int id, int[] toDel){
        //env.logger.info("player " + (id+1) + " set is valid: "+toDel[0]+", "+toDel[1]+", "+toDel[2]);
        blockPlayers();
        try {
            queueSafety.acquire();
        } catch (InterruptedException ignored) {
        }
        removeUnvalidTokens(toDel);
        int setsToCheckSize = setsToCheck.size();
        for (int i = 0; i < setsToCheckSize; i++) {
            Set tmp = setsToCheck.remove();
            if(tmp.getSize()==3)
                setsToCheck.add(tmp);
            else{
                freezeArray[tmp.getId()] = 0;//unblock the player because his set isnt valid for checking
                players[tmp.getId()].setNoLongerValidforCheck();//v8
            }
        }
        freezeArray[id] = System.currentTimeMillis()+env.config.pointFreezeMillis;
        players[id].point();
        removeCardsFromTable(toDel);
        placeCardsOnTable();
        queueSafety.release();
        updateTimerDisplay(true); // reset time after a valid set was found
    }

    /**
     * Removes(for all players) tokens that on cards that not longer placed in the table
     */
    private void removeUnvalidTokens(int[] toDel){
        for (Player player : players) {
            player.removeTokensByDealer(toDel[0]);
            player.removeTokensByDealer(toDel[1]);
            player.removeTokensByDealer(toDel[2]);
        }
    }
    
    /**
     * notValidSet functions continues the checkSet functions
     */
    private void notValidSet(int id){
        freezeArray[id] = System.currentTimeMillis()+env.config.penaltyFreezeMillis;
        players[id].penalty();
        checkToUnblock();
    }

    //for tests
    public ArrayBlockingQueue<Set> getsetsToCheck(){
        return setsToCheck;
    }

}
