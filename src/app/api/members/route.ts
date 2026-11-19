import { addBoardMember, listBoardMembers } from '@/lib/board/members';
import { getBoard } from '@/lib/board/get-board';
import { leetCodeUserExists } from '@/lib/leetcode/client';
import { revalidatePath } from 'next/cache';
import { NextResponse } from 'next/server';

const useBackend = process.env.ENABLE_BACKEND_API === 'true';
const BACKEND_URL = process.env.BACKEND_URL;
const USERNAME_PATTERN = /^[a-zA-Z0-9_-]{3,30}$/;

export const GET = async () => {
	if (useBackend && BACKEND_URL) {
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

		if (useBackend && BACKEND_URL) {
			const backendRes = await fetch(`${BACKEND_URL}/api/members`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ username, displayName }),
			});
			const payload = await backendRes.json();
			if (!backendRes.ok) {
				return NextResponse.json(payload, { status: backendRes.status });
			}
			revalidatePath('/');
			return NextResponse.json(payload, { status: 201 });
		}

		const exists = await leetCodeUserExists(username);
		if (!exists) {
			return NextResponse.json({ error: 'That LeetCode username was not found.' }, { status: 404 });
		}

		const member = await addBoardMember(username, displayName);
		revalidatePath('/');
		return NextResponse.json({ member }, { status: 201 });
	} catch (error) {
		console.error('Error joining board:', error);
		return NextResponse.json({ error: 'Failed to join the board' }, { status: 500 });
	}
};
