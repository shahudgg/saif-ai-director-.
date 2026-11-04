# -*- coding: utf-8 -*-
"""assemble.py: Final Video Compiler & Broadcast Delivery Master.
"""

import argparse, os, subprocess, sys

def assemble(shots_dir='shots', mix_path='mix.wav', ass_path='labels.ass', out_path='master.mp4'):
    assert os.path.exists(mix_path), f'Missing audio mix: {mix_path}'
    shots = sorted([os.path.join(shots_dir, f) for f in os.listdir(shots_dir) if f.endswith('.mp4')])
    assert len(shots) > 0, f'No shots found in {shots_dir}/'
    
    print(f'Assembling {len(shots)} shots with {mix_path} into {out_path}...')
    concat_file = '_concat_list.txt'
    with open(concat_file, 'w', encoding='utf-8') as f:
        for s in shots:
            f.write(f"file '{os.path.abspath(s)}'\n".replace('\\', '/'))
            
    # Concatenate silent shots
    cmd_concat = ['ffmpeg', '-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', concat_file, '-c', 'copy', '_silent_master.mp4']
    subprocess.run(cmd_concat, check=True)
    
    # Mux with audio and burn subtitles if ass exists
    cmd_mux = ['ffmpeg', '-y', '-loglevel', 'error', '-i', '_silent_master.mp4', '-i', mix_path]
    if os.path.exists(ass_path):
        cmd_mux += ['-vf', f'ass={ass_path}:shaping=complex']
    cmd_mux += [
        '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '18',
        '-pix_fmt', 'yuv420p', '-threads', '4',
        '-c:a', 'aac', '-b:a', '192k',
        '-shortest', '-movflags', '+faststart',
        out_path
    ]
    subprocess.run(cmd_mux, check=True)
    
    # Cleanup temp
    if os.path.exists(concat_file): os.remove(concat_file)
    if os.path.exists('_silent_master.mp4'): os.remove('_silent_master.mp4')
    
    dur = float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', out_path], capture_output=True, text=True).stdout)
    size_mb = os.path.getsize(out_path) / (1024 * 1024)
    print(f'\n======================================================')
    print(f'BROADCAST DELIVERY READY: {out_path}')
    print(f'Duration: {dur:.2f}s (~{int(dur//60)}m {int(dur%60)}s) | Size: {size_mb:.2f} MB')
    print(f'======================================================\n')

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--shots', default='shots')
    p.add_argument('--mix', default='mix.wav')
    p.add_argument('--ass', default='labels.ass')
    p.add_argument('--out', default='master.mp4')
    args = p.parse_args()
    assemble(args.shots, args.mix, args.ass, args.out)
