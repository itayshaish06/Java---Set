package bguspl.set.ex;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class manages the players' slotsWithToken and slotsToPress
 *
 * @inv id >= 0
 * @inv size >= 0
 * @inv size <= 3
 */
public class Set {
    /**
     * player's id
     */
    private int id;

    /**
     * size of valid data in Array
     */
    private AtomicInteger size;

    /**
     * Array of slotsWithToken or slotsToPress
     */
    private AtomicIntegerArray tokens;

    /**
     * The class constructor.
     * @param id     - the id of the player.
     */
    public Set(int id){
        size = new AtomicInteger(0);
        tokens = new AtomicIntegerArray(new int[3]);
        this.id = id;
        tokens.getAndSet(0, -1);
        tokens.getAndSet(1, -1);
        tokens.getAndSet(2, -1);
    }

    public int getId(){
        return id;
    }

    public int getSize(){
        int tmp =  size.get();
        return tmp;
    }

    /**
     * add 'newvalue' to tokens if possible
     */
    public boolean addCard(int newvalue) {
        boolean result = false;
        int tmpSize;
        if (size.get() < 3) {
            tokens.getAndSet(size.get(), newvalue);
            do{
                tmpSize = size.get();
            }while(!size.compareAndSet(tmpSize, tmpSize+1));
            result = true;
        }
        return result;
    }

    public int getSlot(int index){
        int tmp = tokens.get(index);
        return tmp;
    }

    /**
     * Removes 'toRemove' from tokens if exists in it
     */
    public boolean removeCardfromSlot(int toRemove){
        int index = -1;
        boolean found = false;
        while(!found & index<size.get()-1){
            index++;
            found = tokens.compareAndSet(index, toRemove, -1);
        }
        int last = index+1;
        while(found & last<size.get()){
            tokens.getAndSet(index, tokens.get(last));
            index++;
            last++;
        }
        if(found){
            int tmpSize;
            do{
                tmpSize = size.get();
            }while(!size.compareAndSet(tmpSize, Max(tmpSize-1,0)));
        }
         return found;
    }

    /**
     * Clears Token Array
     */
    public void clear(){
        tokens.getAndSet(0, -1);
        tokens.getAndSet(1, -1);
        tokens.getAndSet(2, -1);
        int tmpSize;
        do{
            tmpSize = size.get();
        }while(!size.compareAndSet(tmpSize, 0));
    }

    private int Max(int a1, int a2) {
        if (a1 > a2)
            return a1;
        return a2;
    }

}
