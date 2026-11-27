import { format, parseISO } from 'date-fns';
import Card from '../Card';
import ProgressBar from '../ProgressBar';
import SourceTag from '../SourceTag';
import { isValidHttpUrl } from '../../utils/urlValidation';
import { ProjectIconTile, ProjectStatusChip } from './projectMeta';

function formatStartDate(dateStr) {
  if (!dateStr) return 'Start date not set';
  try {
    return `Started ${format(parseISO(dateStr), 'MMM d, yyyy')}`;
  } catch {
    return `Started ${dateStr}`;
  }
}

export default function ProjectCard({ project, onOpenProject, onAddMilestone }) {
  const hasLiveUrl = isValidHttpUrl(project.liveUrl);
  const hasRepoUrl = isValidHttpUrl(project.repoUrl);
  const techList = project.techStack || [];
  const visibleTech = techList.slice(0, 4);
  const extraTechCount = Math.max(0, techList.length - visibleTech.length);
  const hasMilestones = (project.totalMilestones ?? 0) > 0 && project.progress !== null && project.progress !== undefined;

  return (
    <Card
      onClick={() => onOpenProject?.(project.id, 'overview')}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onOpenProject?.(project.id, 'overview');
        }
      }}
      className="cursor-pointer flex flex-col justify-between gap-4 hover:shadow-card-hover hover:border-primary/30 transition-all duration-base focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      <div className="space-y-3.5">
        {/* Top row: Icon tile + Title + Status chip */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 min-w-0">
            <ProjectIconTile icon={project.icon} />
            <div className="min-w-0">
              <h3 className="text-base font-bold text-ink truncate">{project.title}</h3>
              <div className="mt-1 flex flex-wrap items-center gap-1.5">
                <ProjectStatusChip status={project.status} />
                {project.dataSource === 'DEMO' && <SourceTag source="DEMO" />}
              </div>
            </div>
          </div>
        </div>

        {/* 2-line description */}
        <p className="text-sm text-body line-clamp-2 min-h-[2.5rem]">
          {project.description || 'No description added yet. Click to open project details.'}
        </p>

        {/* Technology chips */}
        <div className="flex flex-wrap gap-1.5 min-h-[1.75rem]">
          {visibleTech.length === 0 ? (
            <span className="text-xs text-muted">No technologies tagged</span>
          ) : (
            <>
              {visibleTech.map((tech) => (
                <span
                  key={tech}
                  className="rounded-pill border border-border bg-canvas px-2.5 py-0.5 text-[11px] font-medium text-ink"
                >
                  {tech}
                </span>
              ))}
              {extraTechCount > 0 && (
                <span className="rounded-pill border border-border bg-surface px-2 py-0.5 text-[11px] font-medium text-muted">
                  +{extraTechCount}
                </span>
              )}
            </>
          )}
        </div>
      </div>

      <div className="space-y-3 pt-2 border-t border-border/70">
        {/* Progress bar OR empty milestones prompt */}
        {hasMilestones ? (
          <div className="space-y-1">
            <ProgressBar
              value={project.progress}
              label={`Milestones (${project.completedMilestones}/${project.totalMilestones})`}
            />
          </div>
        ) : (
          <div className="flex items-center justify-between gap-2 rounded-xl border border-dashed border-border bg-canvas/70 px-3 py-2">
            <span className="text-xs font-medium text-muted">No milestones yet</span>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onAddMilestone?.(project.id);
              }}
              className="rounded-pill bg-primary-soft px-2.5 py-1 text-xs font-semibold text-primary hover:bg-primary hover:text-white transition-colors duration-fast"
            >
              + Add milestone
            </button>
          </div>
        )}

        {/* Links + Start Date */}
        <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
          <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
            {hasLiveUrl ? (
              <a
                href={project.liveUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 rounded-pill border border-primary/25 bg-primary-soft px-3 py-1 text-xs font-semibold text-primary hover:bg-primary hover:text-white transition-colors duration-fast"
              >
                Live Demo ↗
              </a>
            ) : (
              <span
                aria-disabled="true"
                title="Add a valid http/https Live Demo URL to enable"
                className="inline-flex items-center gap-1 rounded-pill border border-border bg-slate-100 px-3 py-1 text-xs font-medium text-slate-400 cursor-not-allowed select-none"
              >
                Live Demo
              </span>
            )}

            {hasRepoUrl ? (
              <a
                href={project.repoUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 rounded-pill border border-border bg-surface px-3 py-1 text-xs font-semibold text-ink hover:bg-canvas transition-colors duration-fast"
              >
                GitHub ↗
              </a>
            ) : (
              <span
                aria-disabled="true"
                title="Add a valid http/https GitHub URL to enable"
                className="inline-flex items-center gap-1 rounded-pill border border-border bg-slate-100 px-3 py-1 text-xs font-medium text-slate-400 cursor-not-allowed select-none"
              >
                GitHub
              </span>
            )}
          </div>

          <span className="text-[11px] font-medium text-muted">
            {formatStartDate(project.startDate)}
          </span>
        </div>
      </div>
    </Card>
  );
}
