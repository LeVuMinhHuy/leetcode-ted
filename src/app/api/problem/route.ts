import { DB_COLLECTION } from '@/constants/database';
import clientPromise from '@/lib/mongodb';
import { NextResponse } from 'next/server';

const notifyMemberSolved = async (problemId: number, username: string, displayName: string) => {
	const serviceUrl = process.env.NOTIFICATION_SERVICE_URL;
	if (!serviceUrl) return;
	try {
		await fetch(`${serviceUrl}/notifications/member-solved`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ solver: { username, displayName }, problemId }),
		});
	} catch (err) {
		console.error('[notification] Failed to call member-solved endpoint:', err);
	}
};

export async function POST(req: Request) {
	try {
		const client = await clientPromise;

		const { id, status, date, favorite, attempts, username, displayName } = await req.json();

		if (!id) {
			return NextResponse.json({ error: 'Where is the id bro ?' }, { status: 400 });
		}

		const db = client.db();
		const collection = db.collection(DB_COLLECTION);

		await collection.updateOne(
			{ id: +id },
			{
				$set: {
					...(status && { status }),
					...(date && { date }),
					...((favorite === true || favorite === false) && { favorite }),
					...(attempts && { attempts }),
				},
			}
		);

		if (status === 'Accepted' && process.env.ENABLE_SQS_NOTIFICATIONS === 'true') {
			const solverUsername = (username as string | undefined)?.trim() || process.env.LC_USERNAME || 'unknown';
			const solverDisplayName = (displayName as string | undefined)?.trim() || solverUsername;
			notifyMemberSolved(+id, solverUsername, solverDisplayName);
		}

		return NextResponse.json({ message: 'Problem updated successfully' });
	} catch (error) {
		return NextResponse.json({ error: 'Failed to update problem' }, { status: 500 });
	}
}
