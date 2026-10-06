const DERIVED_BASE = 800;
const EASY_WEIGHT = 2;
const MEDIUM_WEIGHT = 6;
const HARD_WEIGHT = 16;
const STREAK_WEIGHT = 10;

export const deriveGrindElo = ({
	easy,
	medium,
	hard,
	currentStreak,
}: {
	easy: number;
	medium: number;
	hard: number;
	currentStreak: number;
}): number =>
	Math.round(
		DERIVED_BASE +
			easy * EASY_WEIGHT +
			medium * MEDIUM_WEIGHT +
			hard * HARD_WEIGHT +
			currentStreak * STREAK_WEIGHT
	);

export const resolveElo = ({
	contestRating,
	easy,
	medium,
	hard,
	currentStreak,
}: {
	contestRating: number | null;
	easy: number;
	medium: number;
	hard: number;
	currentStreak: number;
}): { elo: number; eloSource: 'contest' | 'derived' } => {
	if (contestRating && contestRating > 0) {
		return { elo: Math.round(contestRating), eloSource: 'contest' };
	}

	return {
		elo: deriveGrindElo({ easy, medium, hard, currentStreak }),
		eloSource: 'derived',
	};
};
