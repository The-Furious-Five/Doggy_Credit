export * from './consentimientos.service';
import { ConsentimientosService } from './consentimientos.service';
export * from './evaluaciones.service';
import { EvaluacionesService } from './evaluaciones.service';
export * from './integraciones.service';
import { IntegracionesService } from './integraciones.service';
export * from './solicitantes.service';
import { SolicitantesService } from './solicitantes.service';
export const APIS = [ConsentimientosService, EvaluacionesService, IntegracionesService, SolicitantesService];
