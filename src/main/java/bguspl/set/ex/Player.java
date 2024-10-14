package bguspl.set.ex;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * Set(AtomicIntegerArray) to save slots with token
     */
    private Set slotsWithToken;

    /**
     * Set(AtomicIntegerArray) to save slots to place token
     */
    private Set slotsToPress;

    /**
     * Atomic boolean to prevent player to access table
     */
    private AtomicBoolean tableSafety;

    /**
     * The dealer of the game
     */
    private Dealer dealer;

    /**
     * Timer of possible freeze
     */
    private long freezTime;

    /**
     * Atomic Integer to identify type of freeze
     * value 0 - no freeze
     * value 1 - point freeze
     * value 2 - penalty freeze
     */
    private AtomicInteger typeOfFreeze;

    enum threadPlace {
        Noclass,
        PlayerClass,
        TableClass,
        DealerClass
    }

    /**
     * Enum to identify the current loaction and match interupt type to it
     */
    private threadPlace place;

    /**
     * A boolean to identify if the thread was interupted During Table functions
     */
    private volatile boolean interuptedDuringTable;

    /**
     * A boolean to identify if the thread send his set to check
     */
    private volatile boolean setInCheck;

    /**
     * A boolean to identify if the player thread is in slotsToPress
     */
    private volatile boolean pressSafe;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        slotsToPress = new Set(id);
        slotsWithToken = new Set(id);
        tableSafety = new AtomicBoolean(false);
        typeOfFreeze = new AtomicInteger(0);
        interuptedDuringTable = false;
        setInCheck = false;
        pressSafe = false;
        place = threadPlace.Noclass;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        place = threadPlace.PlayerClass;
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) {
            createArtificialIntelligence();
        }

        dealer.interruptDealer();

        while (!terminate) {
            if ((!terminate) && (!setInCheck) && Thread.interrupted()) {//v8
                //env.logger.info("       "+Thread.currentThread().getName() + " in line 155 - Thread.interrupted()");//v8
                continuerun();
            }
            if ((!terminate) && (!setInCheck) && interuptedDuringTable) {//v8
                //env.logger.info("       "+Thread.currentThread().getName() + " in line 159 - interuptedDuringTable");//v8
                interuptedDuringTable = false;
                continuerun();
            }
            token();
            try {
                Thread.sleep(env.config.turnTimeoutMillis);
            } catch (InterruptedException exc) {
                //env.logger.info("       "+Thread.currentThread().getName() + " in line 166 - InterruptedException");//v8
                if (!terminate)
                    continuerun();
            }
        }

        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The missions that the player thread does when interrupted
     */
    private void continuerun() {
        if (Thread.currentThread() == playerThread) {
            if ((!terminate) && typeOfFreeze.get() == 1) {
                // reduce point freeze
                while ((!terminate) && freezTime + env.config.pointFreezeMillis >= System.currentTimeMillis()) {
                    try {
                        Thread.sleep(env.config.pointFreezeMillis/5);
                        point();
                    } catch (InterruptedException exc3) {
                        //env.logger.info("       "+Thread.currentThread().getName() + " reduce point freeze - InterruptedException");//v8
                        // interupt will be only from terminate
                    }
                }
                if (!terminate) {
                    if(env.config.pointFreezeMillis>0)
                        env.ui.setFreeze(id, 0);
                    typeOfFreeze.set(0);
                    setInCheck = false;
                }
            }
            if ((!terminate) && typeOfFreeze.get() == 2) {
                // reduce penalty freeze
                while ((!terminate) && freezTime + env.config.penaltyFreezeMillis >= System.currentTimeMillis()) {
                    try {
                        Thread.sleep(env.config.penaltyFreezeMillis/5);
                        penalty();
                    } catch (InterruptedException exc4) {
                        //env.logger.info("       "+Thread.currentThread().getName() + " reduce penalty freeze - InterruptedException");//v8
                        // interupt will be only from terminate
                    }
                }
                if (!terminate) {
                    if(env.config.penaltyFreezeMillis>0)
                        env.ui.setFreeze(id, 0);
                    typeOfFreeze.set(0);
                    setInCheck = false;
                }
            }
            if (!terminate)
                token();
        }
    }


    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)

        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            Random rand = new Random();
            while (!terminate) {//v8
                    keyPressed(rand.nextInt(12));
                    sleepAi(3);
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
        try {
            Thread.sleep(2);
        } catch (Exception e) {
        }
    }

    private void sleepAi(int time) {
        if (Thread.currentThread() == aiThread & (!terminate)) {
            try {
                Thread.sleep(time);
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     * 
     * @post - terminate changes to true
     */
    public void terminate() {
        terminate = true;
        playerThread.interrupt();
        if (!human)
            aiThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     * @pre - 1 - slotsToPress contains slot
     * @pre - 2 - slotsToPress does not contains slot
     * @param slot - the slot corresponding to the key pressed.
     * @post - 1 - slotsToPress does not contains slot
     * @post - 2 - slotsToPress contains slot
     */
    public void keyPressed(int slot) {
        if ((!pressSafe) && tryToAccessTable() && (!setInCheck)) {
            if (!slotsToPress.removeCardfromSlot(slot)) {
                if ((!pressSafe) && tryToAccessTable() && (!setInCheck)) {
                    slotsToPress.addCard(slot);
                }
            }

            if (tryToAccessTable()){//v8 && (!setInCheck)) {
                if (place != threadPlace.TableClass)
                    if((!pressSafe) && (!setInCheck))//v8
                        playerThread.interrupt();
                else {
                    if((!pressSafe) && (!setInCheck))//v8
                        interuptedDuringTable = true;
                }
            }
        }
    }

    /**
     * The player thread tries(if possible) to place token on table
     */
    public void token() {
        pressSafe = true;
        int k = slotsToPress.getSize();
        for (int i = 0;(!terminate) && (tryToAccessTable()) && (!setInCheck) && i < k; i++) {
            int tmp = slotsToPress.getSlot(0);
            slotsToPress.removeCardfromSlot(tmp);
            place = threadPlace.TableClass;
            Integer cardInSlot = table.getCardInSlot(tmp);
            place = threadPlace.PlayerClass;
            if (cardInSlot != null) {
                if (slotsWithToken.removeCardfromSlot(tmp)) {
                    removeToken(tmp);
                } else {
                    if (slotsWithToken.addCard(tmp)) {
                        placeToken(tmp);
                        sendSetToCheck();
                    }
                }
            }
        }
        pressSafe = false;
    }

    /**
     * Function for Dealer thread - removes tokens of players from non-valid cards
     */
    public void removeTokensByDealer(int slot) {
        if (slotsWithToken.removeCardfromSlot(slot)) {
            slotsToPress.removeCardfromSlot(slot);
            removeToken(slot);
            setInCheck = false;
        }
    }

    /**
     * Send set to dealer to Check
     */
    public void sendSetToCheck() {
        if (slotsWithToken.getSize() == 3) {
            //env.logger.info(Thread.currentThread().getName() + " is sending SET");
            setInCheck = true;
            //v8 blockPlayer();
            dealer.addSetToCheck(slotsWithToken);
            slotsToPress.clear();
        }
    }

    public void removeToken(int slot) {
        place = threadPlace.TableClass;
        //env.logger.info("player " +id+ " REMOVES token in slot " + slot);
        table.removeToken(id, slot);
        place = threadPlace.PlayerClass;
    }

    public void placeToken(int slot) {
        place = threadPlace.TableClass;
        //env.logger.info("player " +id+ " PLACES token in slot " + slot);
        table.placeToken(id, slot);
        place = threadPlace.PlayerClass;

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        if (Thread.currentThread() != playerThread) {
            score++;
            freezTime = System.currentTimeMillis();
            if (env.config.pointFreezeMillis > 0)
                env.ui.setFreeze(id, env.config.pointFreezeMillis);
            env.ui.setScore(id, score);
            typeOfFreeze.set(1);
            if (place != threadPlace.TableClass)
                playerThread.interrupt();
            else {
                interuptedDuringTable = true;
            }
        } else {
            if (env.config.pointFreezeMillis > 0)
                env.ui.setFreeze(id, env.config.pointFreezeMillis - System.currentTimeMillis() + freezTime);
        }
        // int ignored = table.countCards(); // this part is just for demonstration in
        // the unit tests
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        if (Thread.currentThread() != playerThread) {
            freezTime = System.currentTimeMillis();
            if (env.config.penaltyFreezeMillis > 0)
                env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
            typeOfFreeze.set(2);
            if (place != threadPlace.TableClass){
                //env.logger.info("dealer interupts player "+ id + " and his thread is " + playerThread.getState());
                playerThread.interrupt();
            }
            else {
                interuptedDuringTable = true;
            }
        } else {
            if (env.config.penaltyFreezeMillis > 0)
                env.ui.setFreeze(id, env.config.penaltyFreezeMillis - System.currentTimeMillis() + freezTime);
        }
    }

    public int score() {
        return score;
    }

    public void blockPlayer() {
        tableSafety.set(false);
        //env.logger.info("Thread " + Thread.currentThread().getName() + " blocked player" + id);
    }

    public void unblockPlayer() {
        if(!setInCheck)//v8
            tableSafety.set(true);
        //env.logger.info("Thread " + Thread.currentThread().getName() + " unblocked player" + id);
    }

    public boolean tryToAccessTable() {
        return tableSafety.get() == true;
    }

    public void setNoLongerValidforCheck(){//v8
        setInCheck = false;
    }

    // for tests
    public boolean getTerminate() {
        return terminate;
    }

    // for tests
    public Set getslotsToPress() {
        return slotsToPress;
    }

}
