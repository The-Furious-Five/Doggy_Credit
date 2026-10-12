import { Component, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { map, of, switchMap } from 'rxjs';
import { Evaluacion, EvaluacionesService } from '../api';
import { DsAlert, DsButton, DsEmptyState, DsSpinner } from '../design-system';
import { day } from './client-format';
import { RecommendationList } from './recommendation-list';

/** C-06: pestaña Recomendaciones del perfil: las de la evaluación más reciente del cliente. */
@Component({
  selector: 'dc-applicant-recommendations',
  imports: [DsAlert, DsButton, DsEmptyState, DsSpinner, RecommendationList],
  template: `
    @if (latest.isLoading()) {
      <ds-spinner label="Cargando recomendaciones…" />
    } @else if (latest.error()) {
      <ds-alert tone="danger" heading="No pudimos cargar las recomendaciones">
        <p>Revisa tu conexión y vuelve a intentarlo.</p>
        <button dsButton variant="secondary" (click)="latest.reload()">Reintentar</button>
      </ds-alert>
    } @else if (latest.value(); as assessment) {
      <div class="ds-stack">
        <p class="ds-caption">Según la evaluación del {{ day(assessment.creadoEn) }}. Es una recomendación, no una aprobación.</p>
        <dc-recommendation-list [items]="assessment.evaluacion.recomendaciones" />
      </div>
    } @else {
      <ds-empty-state
        heading="Sin recomendaciones"
        description="Este cliente todavía no tiene evaluaciones. Las recomendaciones salen de su evaluación más reciente."
      />
    }
  `,
})
export class ApplicantRecommendations {
  private readonly api = inject(EvaluacionesService);
  readonly applicantId = input.required<string>();
  readonly day = day;

  /** La evaluación más reciente del cliente, o null si todavía no tiene. */
  readonly latest = rxResource({
    params: () => this.applicantId(),
    stream: ({ params }) =>
      this.api.listarEvaluaciones(params, undefined, 1, 1).pipe(
        switchMap((page) => {
          const summary = page.items[0];
          if (!summary) return of(null);
          return this.api
            .obtenerEvaluacion(summary.id)
            .pipe(map((evaluacion: Evaluacion) => ({ evaluacion, creadoEn: summary.creadoEn })));
        }),
      ),
  });
}
