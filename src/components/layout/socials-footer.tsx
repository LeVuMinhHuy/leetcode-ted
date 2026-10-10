import { ModeToggle } from '@/components/theme/toggle-mode';
import { Button } from '@/components/ui/button';
import { Github, Globe, Instagram } from 'lucide-react';
import { Link } from '../custom/link';

export function SocialsFooter() {
	return (
		<div className='flex flex-col gap-1'>
			<div className='flex justify-center items-center gap-2 p-1'>
				<ModeToggle className='[&>svg]:h-4 [&>svg]:w-4' />
			</div>
		</div>
	);
}
