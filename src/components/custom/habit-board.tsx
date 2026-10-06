'use client';

import React, { useMemo } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import type { DailyContribution } from '@/lib/leetcode/types';
import { cn } from '@/lib/utils';

export type HabitBoardProps = {
	contributions: DailyContribution[];
	year: number;
	availableYears: number[];
	streak: number;
	longestStreak: number;
	totalSolved: number;
	totalQuestions: number;
	displayName: string;
	onYearChange?: (year: number) => void;
};

const getProblemCountMap = (contributions: DailyContribution[]): Map<string, number> => {
	const problemCount = new Map<string, number>();
	contributions.forEach((day) => {
		problemCount.set(day.date, day.count);
	});
	return problemCount;
};

const getColorClass = (count: number): string => {
	if (count === 0) return 'bg-muted';
	if (count === 1) return 'bg-orange-100 dark:bg-orange-950';
	if (count === 2) return 'bg-orange-200 dark:bg-orange-900';
	if (count === 3) return 'bg-orange-300 dark:bg-orange-800';
	if (count === 4) return 'bg-orange-400 dark:bg-orange-700';
	if (count === 5) return 'bg-orange-500 dark:bg-orange-600';
	if (count === 6) return 'bg-orange-600';
	return 'bg-orange-700';
};

const formatDateStr = (date: Date): string => {
	const year = date.getFullYear();
	const month = String(date.getMonth() + 1).padStart(2, '0');
	const day = String(date.getDate()).padStart(2, '0');
	return `${year}-${month}-${day}`;
};

const getMonthWeeks = (month: number, year: number) => {
	const daysInMonth = new Date(year, month + 1, 0).getDate();
	const firstDay = new Date(year, month, 1).getDay();
	const weeks: (Date | null)[][] = [];
	let currentWeek: (Date | null)[] = [];

	const startOffset = firstDay === 0 ? 6 : firstDay - 1;
	for (let i = 0; i < startOffset; i++) {
		currentWeek.push(null);
	}

	for (let day = 1; day <= daysInMonth; day++) {
		const date = new Date(year, month, day);
		currentWeek.push(date);
		if (currentWeek.length === 7) {
			weeks.push([...currentWeek]);
			currentWeek = [];
		}
	}

	if (currentWeek.length > 0) {
		while (currentWeek.length < 7) {
			currentWeek.push(null);
		}
		weeks.push([...currentWeek]);
	}

	return weeks;
};

const HabitBoard = ({
	contributions,
	year,
	availableYears,
	streak,
	longestStreak,
	totalSolved,
	totalQuestions,
	displayName,
	onYearChange,
}: HabitBoardProps) => {
	const problemCountMap = useMemo(() => getProblemCountMap(contributions), [contributions]);
	const months = Array.from({ length: 12 }, (_, i) => i);
	const years = availableYears.length ? [...availableYears].sort((a, b) => b - a) : [year];

	return (
		<Card className='p-2 md:p-4 w-full border-none shadow-none'>
			<div className='flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-6'>
				<div className='flex flex-col gap-2 sm:flex-row sm:gap-8'>
					<span className='text-orange-700 dark:text-orange-400 font-medium flex items-center'>
						<div
							className={`w-2 h-2 rounded-full mr-2 ${
								streak > 0 ? 'bg-orange-400 animate-pulse' : 'bg-muted-foreground'
							}`}
						/>
						Streak:{' '}
						<span className={`font-semibold ml-1 ${streak > 0 ? '' : 'text-muted-foreground'}`}>
							{streak}
						</span>
						<span className='text-muted-foreground font-normal ml-1'>best {longestStreak}</span>
					</span>
					<span className='text-green-700 dark:text-green-400 font-medium'>
						{displayName} solved{' '}
						<span className='font-semibold'>{totalSolved}</span>{' '}
						<span className='font-medium'>{`(of ${totalQuestions})`}</span>
					</span>
				</div>
				{onYearChange ? (
					<div className='flex flex-wrap gap-1'>
						{years.map((entryYear) => (
							<button
								key={entryYear}
								type='button'
								onClick={() => onYearChange(entryYear)}
								className={cn(
									'text-xs px-2 py-1 rounded-md border transition-colors',
									entryYear === year
										? 'border-orange-400 text-orange-700 dark:text-orange-300'
										: 'border-transparent text-muted-foreground hover:text-foreground'
								)}
							>
								{entryYear}
							</button>
						))}
					</div>
				) : null}
			</div>

			<CardContent className='p-0'>
				<TooltipProvider>
					<div className='grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-12 gap-6'>
						{months.map((month) => {
							const weeks = getMonthWeeks(month, year);
							return (
								<div key={month} className='flex flex-col items-center'>
									<div
										className='grid grid-rows-7 gap-y-1 gap-x-4 xs:gap-y-[6px] xs:gap-x-[6px]'
										style={{
											gridTemplateColumns: `repeat(${weeks.length}, minmax(0, 1fr))`,
										}}
									>
										{Array.from({ length: 7 }, (_, rowIndex) => (
											<React.Fragment key={rowIndex}>
												{weeks.map((week, weekIndex) => {
													const date = week[rowIndex];
													if (!date) {
														return <div key={weekIndex} className='w-3 h-3' />;
													}
													const dateStr = formatDateStr(date);
													const problemCount = problemCountMap.get(dateStr) || 0;
													const colorClass = getColorClass(problemCount);

													return (
														<Tooltip key={dateStr}>
															<TooltipTrigger>
																<div
																	className={`w-3 h-3 rounded-sm ${colorClass} cursor-pointer`}
																/>
															</TooltipTrigger>
															<TooltipContent>
																<p>
																	{dateStr}: {problemCount}{' '}
																	{problemCount === 1 ? 'submission' : 'submissions'}
																</p>
															</TooltipContent>
														</Tooltip>
													);
												})}
											</React.Fragment>
										))}
									</div>
									<div className='text-center text-xs md:text-sm text-muted-foreground mt-2'>
										{new Date(year, month).toLocaleString('default', {
											month: 'short',
										})}
									</div>
								</div>
							);
						})}
					</div>
				</TooltipProvider>
			</CardContent>
		</Card>
	);
};

export default HabitBoard;
