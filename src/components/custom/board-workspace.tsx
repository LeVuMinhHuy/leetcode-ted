'use client';

import HabitBoard from '@/components/custom/habit-board';
import { JoinBoardForm } from '@/components/custom/join-board-form';
import { Leaderboard } from '@/components/custom/leaderboard';
import { Separator } from '@/components/ui/separator';
import type { DailyContribution, MemberSnapshot } from '@/lib/leetcode/types';
import { useEffect, useMemo, useState } from 'react';

type BoardWorkspaceProps = {
	members: MemberSnapshot[];
};

type YearPayload = {
	year: number;
	contributions: DailyContribution[];
	currentStreak?: number;
	longestStreak?: number;
};

export const BoardWorkspace = ({ members }: BoardWorkspaceProps) => {
	const [selectedUsername, setSelectedUsername] = useState(members[0]?.username ?? '');
	const selected = useMemo(
		() => members.find((member) => member.username === selectedUsername) ?? members[0],
		[members, selectedUsername]
	);
	const [year, setYear] = useState(selected?.year ?? new Date().getFullYear());
	const [contributions, setContributions] = useState<DailyContribution[]>(
		selected?.contributions ?? []
	);
	const [isLoadingYear, setIsLoadingYear] = useState(false);

	useEffect(() => {
		if (!selected) return;
		setYear(selected.year);
		setContributions(selected.contributions);
	}, [selected]);

	const onYearChange = async (nextYear: number) => {
		if (!selected) return;
		setYear(nextYear);
		if (nextYear === selected.year) {
			setContributions(selected.contributions);
			return;
		}

		setIsLoadingYear(true);
		try {
			const response = await fetch(
				`/api/habit?username=${encodeURIComponent(selected.username)}&year=${nextYear}`
			);
			if (!response.ok) return;
			const payload = (await response.json()) as YearPayload;
			setContributions(payload.contributions ?? []);
		} finally {
			setIsLoadingYear(false);
		}
	};

	if (!selected) {
		return (
			<div className='flex flex-col gap-8 w-full'>
				<JoinBoardForm />
				<p className='text-sm text-muted-foreground'>No members yet. Join with a LeetCode username.</p>
			</div>
		);
	}

	return (
		<div className='flex flex-col gap-8 w-full'>
			<section className='space-y-3'>
				<div>
					<h3 className='text-sm font-medium'>Join the board</h3>
					<p className='text-sm text-muted-foreground'>
						Drop a LeetCode username. Stats, streak, and Elo refresh from the live API.
					</p>
				</div>
				<JoinBoardForm />
			</section>

			<Separator />

			<section className='space-y-3'>
				<div>
					<h3 className='text-sm font-medium'>Leaderboard</h3>
					<p className='text-sm text-muted-foreground'>
						Contest Elo when rated. Grind Elo when not. Click a row to open their habit map.
					</p>
				</div>
				<Leaderboard
					members={members}
					selectedUsername={selected.username}
					onSelect={setSelectedUsername}
				/>
			</section>

			<Separator />

			<section className={isLoadingYear ? 'opacity-60 transition-opacity' : ''}>
				<HabitBoard
					contributions={contributions}
					year={year}
					availableYears={selected.availableYears}
					streak={selected.currentStreak}
					longestStreak={selected.longestStreak}
					totalSolved={selected.totalSolved}
					totalQuestions={selected.totalQuestions}
					displayName={selected.displayName}
					onYearChange={onYearChange}
				/>
			</section>
		</div>
	);
};
