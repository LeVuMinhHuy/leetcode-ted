import { BACKEND_ENABLED, backendFetch, withBackendFallback } from '@/lib/backend/client';
import { addBoardMember, listBoardMembers } from '@/lib/board/members';
import { getBoard } from '@/lib/board/get-board';
import { leetCodeUserExists } from '@/lib/leetcode/client';
import { revalidatePath } from 'next/cache';
import { NextResponse } from 'next/server';

const USERNAME_PATTERN = /^[a-zA-Z0-9_-]{3,30}$/;

type JoinResult = { status: number; body: unknown };

const joinViaBackend = async (username: string, displayName?: string): Promise<JoinResult> => {
	const response = await backendFetch('/api/members', {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ username, displayName }),
	});
	return { status: response.status, body: await response.json() };
};

// Safe to run after a backend timeout: addBoardMember is an idempotent upsert.
const joinDirect = async (username: string, displayName?: string): Promise<JoinResult> => {
	if (!(await leetCodeUserExists(username))) {
		return { status: 404, body: { error: 'That LeetCode username was not found.' } };
	}
	const member = await addBoardMember(username, displayName);
	return { status: 201, body: { member } };
};

export const GET = async () => {
	if (BACKEND_ENABLED) {
		const members = await getBoard();
		return NextResponse.json({ members });
	}
	const members = await listBoardMembers();
	return NextResponse.json({ members });
};

export const POST = async (request: Request) => {
	try {
		const body = await request.json();
		const username = body.username?.trim() ?? '';
		const displayName = body.displayName?.trim();

		if (!USERNAME_PATTERN.test(username)) {
			return NextResponse.json(
				{ error: 'Use a valid LeetCode username (3-30 letters, numbers, _ or -).' },
				{ status: 400 }
			);
		}

		const result = await withBackendFallback(
			'join',
			() => joinViaBackend(username, displayName),
			() => joinDirect(username, displayName)
		);
		if (result.status === 201) revalidatePath('/');
		return NextResponse.json(result.body, { status: result.status });
	} catch (error) {
		console.error('Error joining board:', error);
		return NextResponse.json({ error: 'Failed to join the board' }, { status: 500 });
	}
};
