package szte.mi;

import szte.mi.Move;

public interface SuperPlayer {
    int getMoveRow();
    int getMoveCol();

    void enemyPlayMove(int row, int col);
    Move nextMove(Move prevMove, long tOpponent, long t);
    void init(int order, long t, java.util.Random rnd);
}
