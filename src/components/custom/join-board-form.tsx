'use client';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useRouter } from 'next/navigation';
import { type FormEvent, useState, useTransition } from 'react';
import { toast } from 'sonner';

export const JoinBoardForm = () => {
	const router = useRouter();
	const [username, setUsername] = useState('');
	const [displayName, setDisplayName] = useState('');
	const [isPending, startTransition] = useTransition();

	const onSubmit = (event: FormEvent<HTMLFormElement>) => {
		event.preventDefault();

		startTransition(async () => {
			const response = await fetch('/api/members', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					username,
					displayName: displayName || undefined,
				}),
			});

			const payload = (await response.json()) as { error?: string };
			if (!response.ok) {
				toast.error(payload.error || 'Could not join the board');
				return;
			}

			toast.success('Welcome to the board');
			setUsername('');
			setDisplayName('');
			router.refresh();
		});
	};

	return (
		<form onSubmit={onSubmit} className='flex flex-col gap-3 sm:flex-row sm:items-end'>
			<div className='flex-1 space-y-1.5'>
				<Label htmlFor='leetcode-username'>LeetCode username</Label>
				<Input
					id='leetcode-username'
					name='username'
					placeholder='tedvu'
					value={username}
					onChange={(event) => setUsername(event.target.value)}
					required
					minLength={3}
					maxLength={30}
					autoComplete='off'
				/>
			</div>
			<div className='flex-1 space-y-1.5'>
				<Label htmlFor='display-name'>Display name (optional)</Label>
				<Input
					id='display-name'
					name='displayName'
					placeholder='Ted'
					value={displayName}
					onChange={(event) => setDisplayName(event.target.value)}
					maxLength={40}
					autoComplete='off'
				/>
			</div>
			<Button type='submit' disabled={isPending || username.trim().length < 3}>
				{isPending ? 'Joining…' : 'Join board'}
			</Button>
		</form>
	);
};
