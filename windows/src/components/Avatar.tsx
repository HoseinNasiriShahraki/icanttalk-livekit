import type { CSSProperties } from 'react';
import { avatarUrl } from '../models';

export default function Avatar({
  avatarId,
  label,
  className = '',
  style,
}: {
  avatarId: string;
  label: string;
  className?: string;
  style?: CSSProperties;
}) {
  return (
    <img
      className={`avatar-image ${className}`.trim()}
      src={avatarUrl(avatarId)}
      alt={`${label} profile picture`}
      draggable={false}
      style={style}
    />
  );
}
