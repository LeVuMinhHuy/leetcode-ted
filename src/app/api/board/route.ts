import { getBoard } from '@/lib/board/get-board';
import { NextResponse } from 'next/server';

export const GET = async () => {
	try {
		const members = await getBoard();
		return NextResponse.json({ members });
	} catch (error) {
		console.error('Error fetching board:', error);
		return NextResponse.json({ error: 'Failed to fetch board' }, { status: 500 });
	}
};
