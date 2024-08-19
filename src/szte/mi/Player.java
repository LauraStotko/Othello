package szte.mi;

import szte.mi.Move;

public interface Player extends SuperPlayer {
	
	//after this method returns, getMoveRow() and getMoveCol() should return the next move row and col
	Move nextMove(Move prevMove, long tOpponent, long t);

	//returns the last move played
	//should unblock anything waiting
	void init(int order, long t, java.util.Random rnd);

}