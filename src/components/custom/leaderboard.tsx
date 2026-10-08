'use client';

import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { MemberSnapshot } from '@/lib/leetcode/types';

type LeaderboardProps = {
	members: MemberSnapshot[];
	selectedUsername: string;
	onSelect: (username: string) => void;
};

export const Leaderboard = ({ members, selectedUsername, onSelect }: LeaderboardProps) => (
	<div className='overflow-x-auto'>
		<table className='w-full text-sm'>
			<thead>
				<tr className='text-left text-muted-foreground border-b'>
					<th className='py-2 pr-3 font-medium'>#</th>
					<th className='py-2 pr-3 font-medium'>Member</th>
					<th className='py-2 pr-3 font-medium'>Elo</th>
					<th className='py-2 pr-3 font-medium'>Streak</th>
					<th className='py-2 pr-3 font-medium'>Solved</th>
					<th className='py-2 font-medium'>E / M / H</th>
				</tr>
			</thead>
			<tbody>
				{members.map((member, index) => {
					const isSelected = member.username === selectedUsername;
					return (
						<tr
							key={member.username}
							onClick={() => onSelect(member.username)}
							className={cn(
								'border-b last:border-0 cursor-pointer transition-colors',
								isSelected ? 'bg-orange-50 dark:bg-orange-950/30' : 'hover:bg-muted/60',
								member.error && 'opacity-60'
							)}
						>
							<td className='py-3 pr-3 tabular-nums text-muted-foreground'>{index + 1}</td>
							<td className='py-3 pr-3'>
								<div className='flex items-center gap-3 min-w-[160px]'>
									{member.avatar ? (
										<img
											src={member.avatar}
											alt={member.displayName}
											className='h-8 w-8 rounded-full object-cover'
										/>
									) : (
										<div className='h-8 w-8 rounded-full bg-muted' />
									)}
									<div className='min-w-0'>
										<p className='font-medium truncate'>{member.displayName}</p>
										<p className='text-xs text-muted-foreground truncate'>@{member.username}</p>
									</div>
								</div>
							</td>
							<td className='py-3 pr-3'>
								<div className='flex items-center gap-2'>
									<span className='tabular-nums font-semibold'>{member.elo}</span>
									<Badge variant='outline' className='font-normal text-[10px] px-1.5'>
										{member.eloSource === 'contest' ? 'contest' : 'grind'}
									</Badge>
								</div>
							</td>
							<td className='py-3 pr-3'>
								<span
									className={cn(
										'tabular-nums font-medium',
										member.currentStreak > 0
											? 'text-orange-700 dark:text-orange-400'
											: 'text-muted-foreground'
									)}
								>
									{member.currentStreak}
								</span>
								<span className='text-muted-foreground text-xs ml-1'>/ {member.longestStreak}</span>
							</td>
							<td className='py-3 pr-3 tabular-nums'>{member.totalSolved}</td>
							<td className='py-3 tabular-nums text-muted-foreground'>
								{member.easy} / {member.medium} / {member.hard}
							</td>
						</tr>
					);
				})}
			</tbody>
		</table>
	</div>
);
