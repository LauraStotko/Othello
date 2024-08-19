
public interface ReversiSpectator {
	//row and col are the row and column of the last move, -1 -1 for the start of the game
	public void gameUpdated(int row, int col);
	
	//should unblock anything waiting
	public void quitGame();
}
