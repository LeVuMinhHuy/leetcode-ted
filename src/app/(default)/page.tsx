import { BoardWorkspace } from '@/components/custom/board-workspace';
import { getBoard } from '@/lib/board/get-board';

export const dynamic = 'force-dynamic';

export default async function Page() {
	const members = await getBoard();

	return <BoardWorkspace members={members} />;
}
