

import szte.mi.Move;
import szte.mi.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

enum Difficulty {
	Easy, Normal, Hard, Brutal,
}

public class ReversiAI extends Move implements Player {

	final static int WIN = 2000000000;
	final static int LOSE = -WIN;
	final static int DRAW = WIN / 2;

	int minimumSearchDepth = 4;

	int minDepth = 1000;
	int maxDepth = 0;
	// variables to calculate how long it takes to initialize
	long[] initTime;
	long[] numMoves;
	long[] numAtMoves;
	boolean outOfMemory; // based on depth not total moves
	// variables to change the heuristic and stuff
	int cutoff; // when to start allocating more of the
				// remaining time
	int nodesCreated;

	int[] dirOffsets;
	int[] queue;
	int[] baseBoardWeights; // board weights for regular pieces
	int[] perimBoardWeights; // board weights for regular pieces
	int[] permBoardWeights; // weight after it becomes perm
	byte[] numNeighbors;
	int boardSize;
	int numBoardSquares;
	private int order;
	private long timeRemaining;
	private Random rnd;
	byte[] rowColDiagCounts; // diag \, col, diag / , row
	int[] rowColDiagCountIndicies; // sets of 4

	SearchTree head;
	long totalTime;
	long turnTimeLimit;

	int difficulty;
	final static int EASY = 1, NORMAL = 2, HARD = 3, BRUTAL = 4, BRUTALER = 5;

	public ReversiAI(int boardSize, int difficulty, long totalTime,
			long turnTimeLimit, boolean first,int x,int y) {
        super(x,y);
		this.totalTime = totalTime;
		this.turnTimeLimit = turnTimeLimit;
		initTime = new long[boardSize * boardSize + 2];
		numMoves = new long[boardSize * boardSize + 2];
		numAtMoves = new long[boardSize * boardSize + 2];
		int boardDim = boardSize + 2;
		dirOffsets = new int[] { -boardDim - 1, -boardDim, -boardDim + 1, -1,
				boardDim - 1, boardDim + 1, boardDim, 1 };

		this.difficulty = difficulty;
		initHeuristics(boardSize);

		head = new SearchTree(boardSize, first);
		head.undoCountChanges();

		switch (difficulty) {
		case BRUTALER:
			minimumSearchDepth = 4;
			break;
		case BRUTAL:
			if (boardSize <= 12) {
				minimumSearchDepth = 4;
				break;
			}
		case HARD:
			minimumSearchDepth = 2;
			break;
		case NORMAL:
			minimumSearchDepth = 2;
		case EASY:
			minimumSearchDepth = 1;
			break;
		default:
			break;
		}
	}

	// used as a helper for initHeuristics, max is inclusive
	private int flips(int pos, int max) {
		int left = Math.max(pos - 1, 0); // things between left most and the pos
		int right = Math.max(max - pos - 1, 0); // things between right most and
												// the pos
		return choose2(left) + choose2(right);
	}

	// used as a helper for initHeuristics, max inclusive
	private int flippedBy(int pos, int max) {
		int left = pos;
		int right = max - pos;
		return left * right;
	}

	private void initHeuristics(int boardSize) {
		this.boardSize = boardSize;
		int boardDim = boardSize + 2;
		int numSquares = boardSize * boardSize;
		numBoardSquares = boardDim * boardDim;

		// maximize depth and time for final squares
		cutoff = numSquares - 23;

		permBoardWeights = new int[numBoardSquares];
		baseBoardWeights = new int[numBoardSquares];
		perimBoardWeights = new int[numBoardSquares];
		numNeighbors = new byte[numBoardSquares];
		queue = new int[numSquares * 9 + 1];

		// set the counts
		rowColDiagCounts = new byte[2 * (boardSize + boardSize + boardSize - 1)];
		rowColDiagCountIndicies = new int[numBoardSquares * 4];
		for (int i = 0; i < boardSize; i++)
			for (int j = 0; j < boardSize; j++) {
				int pos4 = ((i + 1) * boardDim + (j + 1)) << 2;
				rowColDiagCountIndicies[pos4] = (((boardSize - 1) - j) + i); // \diag
				rowColDiagCountIndicies[pos4 + 1] = ((boardSize * 2 - 1) + j); // |
																				// col
				rowColDiagCountIndicies[pos4 + 2] = ((boardSize * 2 - 1 + boardSize)
						+ i + j);// diag /*/
				rowColDiagCountIndicies[pos4 + 3] = (((boardSize * 2 - 1) * 2 + boardSize) + i);// -
																								// row
			}

		// initialize the maximum for the counts
		for (int i = 0; i < boardSize; i++) {
			rowColDiagCounts[(boardSize * 2 - 1) + i] = (byte) boardSize;
			rowColDiagCounts[((boardSize * 2 - 1) * 2 + boardSize) + i] = (byte) (boardSize);
		}
		for (int i = 0; i < boardSize; i++) {
			rowColDiagCounts[i] = (byte) (i + 1);
			rowColDiagCounts[boardSize * 2 - 2 - i] = (byte) (i + 1);
			rowColDiagCounts[boardSize * 3 - 1 + i] = (byte) (i + 1);
			rowColDiagCounts[boardSize * 5 - 3 - i] = (byte) (i + 1);
		}

		// include the counts for the initial pieces
		for (int i = boardSize / 2 - 1; i <= boardSize / 2; i++)
			for (int j = boardSize / 2 - 1; j <= boardSize / 2; j++) {
				int pos4 = ((i + 1) * boardDim + j + 1) << 2;
				for (int k = 0; k < 4; k++)
					rowColDiagCounts[rowColDiagCountIndicies[pos4 + k]]--;
			}

		// calculate the board weights as num things it can flip + 1 / things
		// that flip it +1
		for (int i = 0; i < boardSize; i++)
			for (int j = 0; j < boardSize; j++) {
				int pos = (i + 1) * boardDim + (j + 1);
				int canFlip = flips(i, boardSize - 1)
						+ flips(j, boardSize - 1)
						+ flips(Math.min(j, boardSize - 1 - i), boardSize - 1
								- Math.abs(i + j - (boardSize - 1)))
						+ flips(Math.min(boardSize - 1 - j, boardSize - 1 - i),
								boardSize - 1 - Math.abs(-j + i));

				int canBeFlippedBy = flippedBy(i, boardSize - 1)
						+ flippedBy(j, boardSize - 1)
						+ flippedBy(Math.min(j, boardSize - 1 - i), boardSize
								- 1 - Math.abs(i + j - (boardSize - 1)))
						+ flippedBy(
								Math.min(boardSize - 1 - j, boardSize - 1 - i),
								boardSize - 1 - Math.abs(-j + i));
				// baseBoardWeights will be normalized, others not
				switch (difficulty) {
				case BRUTALER:
					baseBoardWeights[pos] = canFlip - canBeFlippedBy;
					perimBoardWeights[pos] = (3 * canFlip) - canBeFlippedBy;
					permBoardWeights[pos] = 3 * canFlip;
					break;
				case BRUTAL:
					baseBoardWeights[pos] = canFlip - canBeFlippedBy;
					perimBoardWeights[pos] = (3 * canFlip) - canBeFlippedBy;
					permBoardWeights[pos] = 3 * canFlip;
					break;
				case HARD:
					// baseBoardWeights[pos] = canFlip - canBeFlippedBy + 1;
					perimBoardWeights[pos] = (canFlip - canBeFlippedBy + 1);
					permBoardWeights[pos] = (canFlip + 1);
					break;
				case NORMAL:
					baseBoardWeights[pos] = canFlip - canBeFlippedBy + 1;
					permBoardWeights[pos] = (canFlip + 1);
					break;
				case EASY:
					baseBoardWeights[pos] = canFlip - canBeFlippedBy + 1;
					break;
				default:
					break;
				}
			}

		// record the numNeighbors
		for (int i = 0; i < boardSize; i++) {
			for (int j = 0; j < boardSize; j++) {
				int pos = (i + 1) * boardDim + (j + 1);
				if (i == 0 || j == 0 || i == boardSize - 1
						|| j == boardSize - 1) {
					if ((i == 0 || i == boardSize - 1)
							&& (j == 0 || j == boardSize - 1))
						numNeighbors[pos] = 3;
					else
						numNeighbors[pos] = 5;
				} else
					numNeighbors[pos] = 8;
			}
		}

		// account for the allowing the opponent to place a piece

		final int cornerOG = baseBoardWeights[boardDim + 1];

		int[] boardWeightsNew = baseBoardWeights.clone();
		final int maxIterations = difficulty >= BRUTAL ? 4 : 4;
		for (int iterations = 0; iterations < maxIterations; iterations++) {
			for (int i = 0; i < boardSize; i++) {
				for (int j = 0; j < boardSize; j++) {
					int pos = (i + 1) * boardDim + (j + 1);
					for (int k = 0; k < 8; k++) {
						boardWeightsNew[pos + dirOffsets[k]] -= baseBoardWeights[pos]
								/ (numNeighbors[pos]);
					}
				}
			}
			for (int i = 0; i < numBoardSquares; i++)
				baseBoardWeights[i] = boardWeightsNew[i];
		}

		// rescale everything
		final int cornerNew = baseBoardWeights[boardDim + 1];
		for (int i = 0; i < numBoardSquares; i++)
			if (baseBoardWeights[i] != 0)
				baseBoardWeights[i] = baseBoardWeights[i] + cornerOG
						- cornerNew;
	}

	private static int choose2(int x) {
		return (x) * (x + 1) / 2;
	}
	@Override
	public void init(int order, long t, Random rnd) {
		this.order = order;
		this.timeRemaining = t;
		this.rnd = rnd;

		System.out.println("Reversi Console szte.mi.Player " + (order + 1) + " initialized.");
		System.out.println("Remaining time: " + timeRemaining + " ms");
	}
	@Override
	public Move nextMove(Move prevMove, long tOpponent, long t) {
		Move move = new Move(1,2);
		long startTime = System.currentTimeMillis();
		nodesCreated = 0;
		minDepth = 1000;
		maxDepth = 0;
		outOfMemory = false;
		long start = System.currentTimeMillis();
		long duration = Math.min(
				head.totalMoves >= cutoff ? totalTime * 40 / 100 : totalTime
						/ ((boardSize * boardSize) - head.totalMoves),
				turnTimeLimit);

		System.out.println("\nTime remaining: " + totalTime / 1000 + "s"
				+ ", Turn time: " + duration + "ms\nAI thinking...");
		SearchTree nextHead = head.performSearch(duration);
		if (difficulty >= BRUTAL) {
			SearchTree oldNextHead = null;
			int oldNodesCreated = 0;
			while (System.currentTimeMillis() - start < duration
					&& nextHead != oldNextHead
					&& nodesCreated != oldNodesCreated) {
				if (oldNextHead != null)
					System.out.println("Expanded Search - "
							+ (nodesCreated - oldNodesCreated) + " nodes.");
				oldNodesCreated = nodesCreated;
				head.applyCountChanges();
				nextHead.performSearch(duration
						- (System.currentTimeMillis() - start));
				head.undoCountChanges();
				oldNextHead = nextHead;
				nextHead = head.performSearch(0);


			}
		}

		head = head.makeMove(nextHead.move);
		// find the best heuristic
		long timeSpent = System.currentTimeMillis() - startTime;
		System.out.println("szte.mi.Move: "
				+ Reversi.moveToString(getMoveRow(), getMoveCol())
				+ ", Estimate: " + head.heuristic);
		System.out.println("Min Depth: " + minDepth + ", Max depth: "
				+ maxDepth + ", New nodes: " + nodesCreated + ", Time Spent: "
				+ timeSpent + "ms");
		totalTime -= timeSpent; // update total
								// time
 return move;
	}


	public int getMoveRow() {
		return (head.move / (boardSize + 2)) - 1;
	}


	public int getMoveCol() {
		return (head.move % (boardSize + 2)) - 1;
	}

	public void enemyPlayMove(int row, int col) {
		head = head.makeMove((row + 1) * (boardSize + 2) + col + 1);
	}

	private class SearchTree implements Comparable<SearchTree> {
		// used for board
		final static byte PERIMETER = 2;
		final static byte OUT = 4;
		final static byte ENEMY = -1;
		final static byte EMPTY = 0;
		final static byte AI = 1;

		// type of move just played and position
		final byte type;
		final byte parentType;
		final int move;
		final int totalMoves;

		// set before initialization
		private int heuristic; // value is > 0 if AI is winning < 0 o.w
		private int pieceHeuristic; // heuristic for just the pieces on the
									// board;
		// removed after initialization
		byte[] board;
		private ArrayList<Integer> perimeter;
		private byte[] boardStruct;
		// set after initialization
		private boolean initialized;
		private ArrayList<SearchTree> movesSearch;

		// used for boardstruct
		final static byte normal = 0;
		final static byte permPerim = 1;
		final static byte perm = 2;
		final static byte out = 3;

		SearchTree(int boardSize, boolean aiFirst) {
			boardStruct = new byte[numBoardSquares];

			int boardDim = boardSize + 2;

			// set the perm squares
			// set the outside
			for (int i = 0; i < boardDim; i++) {
				boardStruct[(boardDim - 1) * boardDim + i] = out;
				boardStruct[i] = out;
				boardStruct[i * boardDim] = out;
				boardStruct[i * boardDim + boardDim - 1] = out;
			}
			// set corners
			boardStruct[boardDim + 1] = permPerim;
			boardStruct[boardDim + boardSize] = permPerim;
			boardStruct[1 + boardSize * boardDim] = permPerim;
			boardStruct[numBoardSquares - boardDim - 2] = permPerim;

			// hardcode the initial board
			board = new byte[numBoardSquares];
			for (int i = 0; i < boardDim; i++) {
				board[(boardDim - 1) * boardDim + i] = OUT;
				board[i] = OUT;
				board[i * boardDim] = OUT;
				board[i * boardDim + boardDim - 1] = OUT;
			}
			type = aiFirst ? ENEMY : AI;
			parentType = (byte) -type;
			board[(boardSize / 2) * boardDim + boardSize / 2] = type;
			board[(boardSize / 2) * boardDim + boardSize / 2 + 1] = (byte) -type;
			board[(boardSize / 2 + 1) * boardDim + boardSize / 2 + 1] = type;
			board[(boardSize / 2 + 1) * boardDim + boardSize / 2] = (byte) -type;
			totalMoves = 4;
			move = (boardSize / 2) * boardDim + boardSize / 2;

			heuristic = 0;
			pieceHeuristic = 0;

			// find the perimeter and moves
			movesSearch = new ArrayList<SearchTree>(4);
			perimeter = new ArrayList<Integer>(12);
			for (int rO = -1; rO <= 2; rO++)
				for (int cO = -1; cO <= 2; cO++) {
					int pos = (boardSize / 2 + rO) * boardDim + boardSize / 2
							+ cO;
					if (board[pos] == EMPTY) {
						board[pos] = PERIMETER;
						perimeter.add(pos);
					}
				}
			for (Integer p : perimeter)
				if (verifyPossibleMove(p, (byte) -type))
					movesSearch.add(new SearchTree(this, (byte) -type, p));

			initialized = true;
		}

		public void applyCountChanges() {
			int pos4 = move << 2;
			for (int i = 0; i < 4; i++)
				rowColDiagCounts[rowColDiagCountIndicies[pos4 + i]]--;
		}

		public void undoCountChanges() {
			int pos4 = move << 2;
			for (int i = 0; i < 4; i++)
				rowColDiagCounts[rowColDiagCountIndicies[pos4 + i]]++;
		}

		private SearchTree(SearchTree parent, byte t, int move) {
			this.move = move;
			this.parentType = parent.type;
			this.totalMoves = parent.totalMoves + 1;
			this.boardStruct = parent.boardStruct;
			board = parent.board.clone();
			type = t;
			initialized = false;

			// queue everything that is on the permPerim or newly on permPerim
			// or neighborChanged to Perm
			int queueTail = 0;
			applyCountChanges();

			if (boardStruct[move] == permPerim
					|| ((rowColDiagCounts[rowColDiagCountIndicies[move << 2]]
							| rowColDiagCounts[rowColDiagCountIndicies[(move << 2) + 1]]
							| rowColDiagCounts[rowColDiagCountIndicies[(move << 2) + 2]] | rowColDiagCounts[rowColDiagCountIndicies[(move << 2) + 3]]) == 0)) {
				boardStruct[move] = permPerim;
				queue[queueTail] = move;
				queueTail = 1;
			}

			board[move] = t;
			int rawDiff = baseBoardWeights[move] * type;
			// flip everything
			for (int i = 0; i < 8; i++) {
				int amount = 1;
				while (board[amount * dirOffsets[i] + move] == -type)
					amount++;
				if (board[amount * dirOffsets[i] + move] == type) {
					for (amount--; amount > 0; amount--) {
						int pos = dirOffsets[i] * amount + move;
						board[pos] = type;
						rawDiff += 2 * baseBoardWeights[pos] * type;
						if (boardStruct[pos] == permPerim
								|| ((rowColDiagCounts[rowColDiagCountIndicies[pos << 2]]
										| rowColDiagCounts[rowColDiagCountIndicies[(pos << 2) + 1]]
										| rowColDiagCounts[rowColDiagCountIndicies[(pos << 2) + 2]] | rowColDiagCounts[rowColDiagCountIndicies[(pos << 2) + 3]]) == 0)) {
							queue[queueTail] = pos;
							queueTail++;
						}
					}
				}
			}

			// calculate the structure changes if anything is queued
			if (queueTail > 0) {
				// make a copy of the old boardStruct
				boardStruct = boardStruct.clone();
				// queue new permanent pieces
				for (int k = 0; k < queueTail; k++) {
					final int pos = queue[k];
					// if the new pos is perm and wasn't already
					if (boardStruct[pos] == permPerim && isPerm(pos)) {
						boardStruct[pos] = perm;
						rawDiff += (permBoardWeights[pos] - baseBoardWeights[pos])
								* board[pos];
						for (int i = 0; i < 8; i++) {
							int posShift = pos + dirOffsets[i];
							// if its normal or a permPerim
							if (boardStruct[posShift] < perm) {
								boardStruct[posShift] = permPerim;
								// if its not empty, add it to queue
								if (board[posShift] % 2 != 0) {
									queue[queueTail] = posShift;
									queueTail++;
								}
							}
						}
					}
				}
			}
			pieceHeuristic = rawDiff + parent.pieceHeuristic;

			perimeter = new ArrayList<Integer>(parent.perimeter.size() + 7);
			for (Integer i : parent.perimeter)
				if (i != move)
					perimeter.add(i);
			for (int i = 0; i < 8; i++) {
				int pos = move + dirOffsets[i];
				if (board[pos] == EMPTY) {
					board[pos] = PERIMETER;
					perimeter.add(pos);
				}
			}
			// compute the mobility heuristic
			int mobilityHeuristic = 0;
			for (Integer perim : perimeter) {
				int total = 0;
				int diff = 0;
				if (boardStruct[perim] == permPerim) {
					for (int i = 0; i < 8; i++) {
						int pos = perim + dirOffsets[i];
						if (boardStruct[pos] == perm) {
							diff -= board[pos] % 2;
							total += 2;
						} else {
							diff += board[pos] % 2;
							total += board[pos] & 1;
						}
					}

				} else {
					for (int i = 0; i < 8; i++) {
						int pos = perim + dirOffsets[i];
						diff += board[pos] % 2;
						total += board[pos] & 1;
					}
				}
				mobilityHeuristic -= (perimBoardWeights[perim] * diff)
						/ Math.max(total, 3);
			}

			heuristic = pieceHeuristic + mobilityHeuristic;
			undoCountChanges();
		}

		// creates the searched moves and updates the structure
		private boolean initialize() {
			long startTime = System.currentTimeMillis();
			try {
				// calculate the possible moves
				ArrayList<SearchTree> newMovesSearch = new ArrayList<SearchTree>(
						perimeter.size());
				applyCountChanges();
				// check everything on the perimeter
				for (Integer i : perimeter)
					if (verifyPossibleMove(i, (byte) -type))
						newMovesSearch
								.add(new SearchTree(this, (byte) -type, i));
				// if no moves for next player
				if (newMovesSearch.size() == 0) {
					for (Integer i : perimeter)
						if (verifyPossibleMove(i, type))
							newMovesSearch.add(new SearchTree(this, type, i));
					if (newMovesSearch.size() == 0) {
						// then game is over, do a raw count and find the winner
						int diff = 0;
						for (int i = 0; i < board.length; i++)
							diff += board[i] % 2;
						heuristic = diff
								+ (diff == 0 ? DRAW : (diff > 0 ? WIN : LOSE));
					}
				}
				undoCountChanges();
				// Hurray no out of memory error! Free all your stuff now
				board = null;
				perimeter = null;
				boardStruct = null;
				movesSearch = newMovesSearch;
				nodesCreated += movesSearch.size();

				// used for checking if init has enough time to be called
				numMoves[totalMoves] += movesSearch.size();
				initTime[totalMoves] += (System.currentTimeMillis() - startTime);
				numAtMoves[totalMoves]++;
				initialized = true;
				return true;
			} catch (OutOfMemoryError e) {
				System.out.println("Out of memory! Returning best answer");
				outOfMemory = true;
				return false;
			}
		}

		public void printBoardStruct() {
			System.out.println();
			for (int i = 0; i < boardSize; i++) {
				for (int j = 0; j < boardSize; j++) {
					int pos = (i + 1) * (boardSize + 2) + (j + 1);
					System.out.print(boardStruct[pos] + " ");
				}
				System.out.println();
			}

			this.applyCountChanges();
			for (int i = 0; i < boardSize; i++) {
				for (int j = 0; j < boardSize; j++) {
					int pos = (i + 1) * (boardSize + 2) + (j + 1);
					for (int k = 0; k < 4; k++)
						System.out
								.print(rowColDiagCounts[rowColDiagCountIndicies[(pos << 2)
										+ k]]
										+ ",");
					System.out.print(" ");
				}
				System.out.println();
			}
			this.undoCountChanges();
			System.out.println();
		}

		private boolean isPerm(int pos) {
			for (int i = 0; i < 4; i++) {
				final int p1 = pos + dirOffsets[i];
				final int p2 = pos - dirOffsets[i];
				// if one is out, the lane is full, or one is perm and the same
				// type, or both perm
				if (rowColDiagCounts[rowColDiagCountIndicies[(pos << 2) + i]] == 0
						|| boardStruct[p1] == out
						|| boardStruct[p2] == out
						|| (boardStruct[p1] == perm && boardStruct[p2] == perm)
						|| (boardStruct[p1] == perm && board[p1] == board[pos])
						|| (boardStruct[p2] == perm && board[p2] == board[pos]))
					continue;
				else
					return false;
			}
			return true;
		}

		private boolean verifyPossibleMove(int pos, byte type) {
			for (int i = 0; i < 8; i++) {
				int j = 1;
				while (board[pos + j * dirOffsets[i]] == -type)
					j++;
				if (j > 1 && board[pos + j * dirOffsets[i]] == type)
					return true;
			}
			return false;
		}

		// returns null indicating an error
		SearchTree performSearch(long duration) {
			long startTime = System.currentTimeMillis();
			int maxBest = WIN + boardSize * boardSize;
			heuristic = performSearch(maxBest, -maxBest, startTime + duration,
					0);
			// find the best heuristic
			for (int i = 0; i < movesSearch.size(); i++)
				if (movesSearch.get(i).heuristic >= heuristic)
					return movesSearch.get(i);
			return null;
		}

		// depth is the current depth
		private int performSearch(int minBest, int maxBest, long endTime,
				int depth) {
			// if its not initialized, try to initialize if there is enough time
			if (!initialized) {
				final boolean shouldInitialize = !initialized
						&& !outOfMemory
						&& (type == AI || depth < minimumSearchDepth
								|| type == parentType || (endTime
								- System.currentTimeMillis() > initTime[totalMoves]
								/ Math.max(numAtMoves[totalMoves], 1)
								+ initTime[totalMoves + 1]
								/ Math.max(numAtMoves[totalMoves + 1], 1)
								* numMoves[totalMoves]
								/ Math.max(numAtMoves[totalMoves + 1], 1)));
				if (!shouldInitialize || !initialize()) {
					minDepth = Math.min(minDepth, depth);
					maxDepth = Math.max(maxDepth, depth);
					return heuristic;
				}
			}
			if (movesSearch.size() == 0) {// game over
				minDepth = Math.min(minDepth, depth);
				maxDepth = Math.max(maxDepth, depth);
				return heuristic;
			}
			Collections.sort(movesSearch);
			// give each branch equal amount of the remaining time
			final int childType = movesSearch.get(0).type;
			int currentBest = (LOSE - boardSize * boardSize + 1) * childType;
			applyCountChanges();
			long minTimePerMove = (endTime - System.currentTimeMillis())
					/ movesSearch.size();
			if (childType == AI) { // maxmimize
				for (int i = 0; i < movesSearch.size(); i++) {
					long currentTime = System.currentTimeMillis();
					int h = movesSearch.get(i).performSearch(
							minBest,
							maxBest,
							currentTime
									+ Math.max((endTime - currentTime) / 3,
											minTimePerMove), depth + 1);
					if (h > currentBest) {
						currentBest = h;
						// alpha/beta pruning
						if (h > maxBest) {
							maxBest = h;
							if (maxBest >= minBest)
								break;
						}
					}
				}
			} else { // minimize
				for (int i = 0; i < movesSearch.size(); i++) {
					long currentTime = System.currentTimeMillis();
					int h = movesSearch.get(i).performSearch(minBest, maxBest,
							currentTime + minTimePerMove, depth + 1);
					if (h < currentBest) {
						currentBest = h;
						// alpha/beta pruning
						if (h < minBest) {
							minBest = h;
							if (maxBest >= minBest)
								break;
						}
					}
				}
			}
			undoCountChanges();
			heuristic = currentBest;
			return heuristic;
		}

		// applies a move by returning the new head
		SearchTree makeMove(int move) {
			applyCountChanges();
			if (!initialized)
				initialize();
			SearchTree newHead = movesSearch.get(0);
			for (int i = 0; i < movesSearch.size(); i++)
				if (movesSearch.get(i).move == move) {
					newHead = movesSearch.get(i);
					break;
				}
			if (!newHead.initialized)
				newHead.initialize();
			System.gc();
			return newHead;
		}

		@Override
		public int compareTo(SearchTree arg0) {
			return (parentType) * (arg0.heuristic - heuristic);
		}
	}


	public void quitGame() {
		//do nothing
	}
}
