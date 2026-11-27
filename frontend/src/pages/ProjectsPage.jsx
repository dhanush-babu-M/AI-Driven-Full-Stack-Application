import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  createProject,
  fetchProfileLinks,
  fetchProjectDetail,
  fetchProjects,
  updateProject,
} from '../api/client';
import Card from '../components/Card';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import ProfileQuickLinkPills from '../components/ProfileQuickLinkPills';
import Toast from '../components/Toast';
import ProjectCard from '../components/projects/ProjectCard';
import ProjectDetailDrawer from '../components/projects/ProjectDetailDrawer';
import ProjectFormModal from '../components/projects/ProjectFormModal';

export default function ProjectsPage() {
  const { projectId: routeProjectId } = useParams();
  const navigate = useNavigate();

  const [mobileTab, setMobileTab] = useState('projects'); // 'projects' | 'applications'
  const [projects, setProjects] = useState([]);
  const [profileLinks, setProfileLinks] = useState(null);
  const [loading, setLoading] = useState(true);

  const [formOpen, setFormOpen] = useState(false);
  const [editingProject, setEditingProject] = useState(null);
  const [savingProject, setSavingProject] = useState(false);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerTab, setDrawerTab] = useState('overview');
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');

  const [toast, setToast] = useState({ message: '', type: 'info' });

  const showToast = useCallback((message, type = 'info') => {
    setToast({ message, type });
  }, []);

  const loadProjectsAndLinks = useCallback(async () => {
    try {
      const [projList, links] = await Promise.all([
        fetchProjects(),
        fetchProfileLinks().catch(() => null),
      ]);
      setProjects(projList || []);
      if (links) setProfileLinks(links);
    } catch (err) {
      showToast(err?.message || 'Unable to load projects.', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  const loadDetail = useCallback(async (id) => {
    setDetailLoading(true);
    setDetailError('');
    try {
      const data = await fetchProjectDetail(id);
      setDetail(data);
    } catch (err) {
      setDetail(null);
      setDetailError(err?.message || 'Project not found or you do not have access.');
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProjectsAndLinks();
  }, [loadProjectsAndLinks]);

  useEffect(() => {
    if (routeProjectId) {
      setDrawerOpen(true);
      loadDetail(routeProjectId);
    }
  }, [routeProjectId, loadDetail]);

  const openProjectDrawer = async (id, tab = 'overview') => {
    setDrawerTab(tab);
    setDrawerOpen(true);
    navigate(`/app/projects/${id}`, { replace: false });
    await loadDetail(id);
  };

  const closeProjectDrawer = () => {
    setDrawerOpen(false);
    setDetail(null);
    setDetailError('');
    if (routeProjectId) {
      navigate('/app/projects', { replace: true });
    }
  };

  const handleSaveProject = async (payload) => {
    setSavingProject(true);
    try {
      if (editingProject) {
        const updated = await updateProject(editingProject.id, payload);
        showToast('Project updated.', 'success');
        await loadProjectsAndLinks();
        if (drawerOpen && detail?.project?.id === editingProject.id) {
          setDetail(updated);
        }
      } else {
        const created = await createProject(payload);
        showToast('Project created.', 'success');
        await loadProjectsAndLinks();
        if (created?.project?.id) {
          setDetail(created);
          setDrawerTab('overview');
          setDrawerOpen(true);
          navigate(`/app/projects/${created.project.id}`);
        }
      }
      return true;
    } catch (err) {
      showToast(err?.message || 'Unable to save project.', 'error');
      return false;
    } finally {
      setSavingProject(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Projects and Applications"
        breadcrumb="Portfolio & Jobs"
        rightSlot={
          <button
            type="button"
            onClick={() => {
              setEditingProject(null);
              setFormOpen(true);
            }}
            className="inline-flex items-center justify-center rounded-pill bg-primary px-4 py-2 text-xs sm:text-sm font-semibold text-white hover:bg-primary-hover transition-colors duration-fast shadow-sm"
          >
            + Add Project
          </button>
        }
      />

      {/* Prominent Three Profile Link Pill Buttons */}
      <Card className="!p-4 sm:!p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-sm font-bold text-ink">Profile Quick Links</h2>
            <p className="text-xs text-muted">
              Your primary portfolio, GitHub, and LeetCode links in one place.
            </p>
          </div>
          <ProfileQuickLinkPills links={profileLinks} />
        </div>
      </Card>

      {/* Mobile Tabs (< md) */}
      <div className="flex md:hidden rounded-pill border border-border bg-surface p-1 gap-1">
        <button
          type="button"
          onClick={() => setMobileTab('projects')}
          className={`flex-1 rounded-pill py-2 text-xs font-semibold transition-colors ${
            mobileTab === 'projects'
              ? 'bg-primary text-white shadow-sm'
              : 'text-body hover:text-ink'
          }`}
        >
          My Projects ({projects.length})
        </button>
        <button
          type="button"
          onClick={() => setMobileTab('applications')}
          className={`flex-1 rounded-pill py-2 text-xs font-semibold transition-colors ${
            mobileTab === 'applications'
              ? 'bg-primary text-white shadow-sm'
              : 'text-body hover:text-ink'
          }`}
        >
          Job Applications
        </button>
      </div>

      {/* SECTION 1: MY PROJECTS (Stacked on desktop, tab on mobile) */}
      <section
        aria-labelledby="my-projects-heading"
        className={mobileTab === 'projects' ? 'block space-y-4' : 'hidden md:block space-y-4'}
      >
        <div className="flex items-center justify-between gap-2">
          <div>
            <h2 id="my-projects-heading" className="text-lg font-bold text-ink">
              My Projects
            </h2>
            <p className="text-xs text-muted">
              Track project milestones, daily work logs, screenshots, and demo links.
            </p>
          </div>
          <span className="rounded-pill border border-border bg-surface px-3 py-1 text-xs font-semibold text-muted">
            {projects.length} {projects.length === 1 ? 'project' : 'projects'}
          </span>
        </div>

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
            {[1, 2, 3].map((n) => (
              <Card key={n} className="animate-pulse min-h-[220px]">
                <div className="h-5 w-40 rounded bg-slate-200" />
                <div className="mt-4 h-12 rounded-xl bg-slate-100" />
                <div className="mt-6 h-4 w-full rounded bg-slate-100" />
              </Card>
            ))}
          </div>
        ) : projects.length === 0 ? (
          <EmptyState
            title="No projects added yet"
            description="Create your first project (like PathPilot) with planned milestones to track real completion."
            action={
              <button
                type="button"
                onClick={() => {
                  setEditingProject(null);
                  setFormOpen(true);
                }}
                className="rounded-pill bg-primary px-5 py-2 text-xs font-semibold text-white hover:bg-primary-hover"
              >
                + Add Project
              </button>
            }
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
            {projects.map((project) => (
              <ProjectCard
                key={project.id}
                project={project}
                onOpenProject={(id, tab) => openProjectDrawer(id, tab)}
                onAddMilestone={(id) => openProjectDrawer(id, 'milestones')}
              />
            ))}
          </div>
        )}
      </section>

      {/* SECTION 2: JOB APPLICATIONS PLACEHOLDER (Stacked on desktop, tab on mobile) */}
      <section
        aria-labelledby="job-apps-heading"
        className={mobileTab === 'applications' ? 'block space-y-4' : 'hidden md:block space-y-4'}
      >
        <div className="flex items-center justify-between gap-2">
          <div>
            <h2 id="job-apps-heading" className="text-lg font-bold text-ink">
              Job Applications
            </h2>
            <p className="text-xs text-muted">
              Application tracking table and job-description skill matching arrive in the next step.
            </p>
          </div>
        </div>

        <Card>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-muted border-collapse">
              <thead>
                <tr className="border-b border-border text-[11px] uppercase tracking-wider">
                  <th className="py-2.5 pr-4 font-semibold">Company</th>
                  <th className="py-2.5 pr-4 font-semibold">Role</th>
                  <th className="py-2.5 pr-4 font-semibold">Status</th>
                  <th className="py-2.5 pr-4 font-semibold">Applied Date</th>
                  <th className="py-2.5 font-semibold">Location</th>
                </tr>
              </thead>
            </table>
          </div>
          <div className="mt-4">
            <EmptyState
              title="Job Applications table coming in Prompt 10"
              description="You will be able to log applications, track status history, and compare job descriptions against your Settings skills here."
            />
          </div>
        </Card>
      </section>

      {/* Add / Edit Project Modal */}
      <ProjectFormModal
        open={formOpen}
        initialProject={editingProject}
        saving={savingProject}
        onClose={() => setFormOpen(false)}
        onSubmit={handleSaveProject}
      />

      {/* Project Detail Drawer */}
      <ProjectDetailDrawer
        open={drawerOpen}
        detail={detail}
        loading={detailLoading}
        error={detailError}
        activeTab={drawerTab}
        onTabChange={setDrawerTab}
        onClose={closeProjectDrawer}
        onEditProject={(proj) => {
          setEditingProject(proj);
          setFormOpen(true);
        }}
        onReloadDetail={loadDetail}
        onProjectsMutated={loadProjectsAndLinks}
        onToast={showToast}
      />

      <Toast
        message={toast.message}
        type={toast.type}
        onDismiss={() => setToast({ message: '', type: 'info' })}
      />
    </div>
  );
}
