export * from './auditoria.service';
import { AuditoriaService } from './auditoria.service';
export * from './consentimientos.service';
import { ConsentimientosService } from './consentimientos.service';
export * from './evaluaciones.service';
import { EvaluacionesService } from './evaluaciones.service';
export * from './solicitantes.service';
import { SolicitantesService } from './solicitantes.service';
export const APIS = [AuditoriaService, ConsentimientosService, EvaluacionesService, SolicitantesService];
